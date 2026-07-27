'use strict';

/**
 * Shared WebRTC client for both sides of a call.
 *
 * Audio only. No video is captured, requested or transmitted: the requirement is a phone
 * replacement, and asking for camera permission would be collecting something nobody needs.
 *
 * STUN is Google's free public server. No TURN is configured, which means a call between two
 * endpoints behind strict symmetric NATs may fail to connect. That is the honest trade for a
 * zero-cost setup; adding a TURN server later (coturn is free software, it just needs a host) is a
 * configuration change, not a redesign.
 */
const ICE_SERVERS = [
  { urls: ['stun:stun.l.google.com:19302', 'stun:stun1.l.google.com:19302'] },
];

class AsktrixCall {
  constructor({ room, role, onState, onRemoteStream }) {
    this.room = room;
    this.role = role;
    this.onState = onState || (() => {});
    this.onRemoteStream = onRemoteStream || (() => {});
    this.pc = null;
    this.token = null;
    this.polling = false;
    this.lastSignalId = 0;
    this.offered = false;
    this.ended = false;
    this.localStream = null;
    this.recorder = null;
    this.chunks = [];
    this.startedAt = null;
  }

  async start(token = null) {
    this.token = token;
    this.onState('requesting-microphone');
    this.localStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
      video: false,
    });

    this.pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
    this.localStream.getTracks().forEach((track) => this.pc.addTrack(track, this.localStream));

    this.pc.ontrack = (event) => {
      const [remote] = event.streams;
      this.remoteStream = remote;
      this.onRemoteStream(remote);
      if (this.role === 'agent') this.startRecording(remote);
    };

    this.pc.onicecandidate = (event) => {
      if (event.candidate) this.send({ type: 'ice', candidate: event.candidate });
    };

    this.pc.onconnectionstatechange = () => {
      const state = this.pc.connectionState;
      if (state === 'connected' && !this.startedAt) {
        this.startedAt = Date.now();
        this.send({ type: 'connected' });
        this.onState('connected');
      }
      if (['failed', 'disconnected'].includes(state)) this.onState('lost');
    };

    // Announce arrival, then poll for the peer's messages. Call setup is a handful of small
    // messages, so polling is entirely adequate and works on any host, including serverless.
    const join = await fetch(`/rtc/${encodeURIComponent(this.room)}/join`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ role: this.role, token: this.token }),
    });
    if (!join.ok) {
      this.onState('rejected', 'This call link is not valid');
      return;
    }

    const state = await join.json();
    this.polling = true;
    this.poll();

    this.onState('waiting');

    // The agent is the caller, so the agent offers as soon as the customer is present.
    if (this.role === 'agent' && state.peerPresent) await this.makeOffer();
  }

  /**
   * Polls for messages addressed to this side.
   *
   * Fast while the call is being set up, then slower once connected, since after that the only
   * message that can arrive is a hangup.
   */
  async poll() {
    while (this.polling) {
      try {
        const response = await fetch(
          `/rtc/${encodeURIComponent(this.room)}/poll?role=${this.role}&since=${this.lastSignalId}`,
        );
        if (response.ok) {
          const data = await response.json();
          for (const message of data.messages) {
            this.lastSignalId = Math.max(this.lastSignalId, message.id);
            await this.handleSignal(message);
          }
          // The agent waits for the customer to open the link before offering.
          if (this.role === 'agent' && data.peerPresent && !this.offered) await this.makeOffer();
          if (data.finished && !this.ended) {
            this.ended = true;
            this.onState('ended');
            this.stop(false);
          }
        }
      } catch { /* a dropped poll is retried on the next tick */ }

      await new Promise((r) => setTimeout(r, this.startedAt ? 3000 : 900));
    }
  }

  async handleSignal(message) {
    switch (message.type) {
      case 'offer': {
        await this.pc.setRemoteDescription(new RTCSessionDescription(message.sdp));
        const answer = await this.pc.createAnswer();
        await this.pc.setLocalDescription(answer);
        this.send({ type: 'answer', sdp: answer });
        break;
      }

      case 'answer':
        await this.pc.setRemoteDescription(new RTCSessionDescription(message.sdp));
        break;

      case 'ice':
        // Candidates can arrive before the remote description is set; ignoring the resulting
        // error is standard and harmless.
        try {
          await this.pc.addIceCandidate(new RTCIceCandidate(message.candidate));
        } catch { /* out-of-order candidate */ }
        break;

      case 'hangup':
      case 'peer-left':
        this.onState('ended');
        this.stop(false);
        break;

      default:
        break;
    }
  }

  async makeOffer() {
    if (this.offered) return;
    this.offered = true;
    const offer = await this.pc.createOffer({ offerToReceiveAudio: true });
    await this.pc.setLocalDescription(offer);
    this.send({ type: 'offer', sdp: offer });
    this.onState('ringing');
  }

  /**
   * Records the call on the agent side (§6).
   *
   * Both directions are mixed through a WebAudio graph before recording, because a MediaRecorder
   * pointed at one stream captures only that half of the conversation.
   *
   * This is legal here precisely because the media never touches the public telephone network, so
   * we own the path. The customer is told, in plain words, on the page they opened.
   */
  startRecording(remoteStream) {
    if (this.recorder || typeof MediaRecorder === 'undefined') return;
    try {
      const context = new AudioContext();
      const destination = context.createMediaStreamDestination();
      context.createMediaStreamSource(this.localStream).connect(destination);
      context.createMediaStreamSource(remoteStream).connect(destination);

      const mime = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4']
        .find((m) => MediaRecorder.isTypeSupported(m));
      if (!mime) return;

      this.recorder = new MediaRecorder(destination.stream, { mimeType: mime, audioBitsPerSecond: 32000 });
      this.recorder.ondataavailable = (event) => {
        if (event.data.size > 0) this.chunks.push(event.data);
      };
      this.recorder.start(5000);
    } catch {
      // A failed recording must never break the call itself.
      this.recorder = null;
    }
  }

  async stop(notifyPeer = true) {
    const duration = this.startedAt ? (Date.now() - this.startedAt) / 1000 : 0;

    if (this.recorder && this.recorder.state !== 'inactive') {
      await new Promise((resolve) => {
        this.recorder.onstop = resolve;
        this.recorder.stop();
      });
      await this.uploadRecording();
    }

    if (notifyPeer) this.send({ type: 'hangup', durationSeconds: duration });

    this.polling = false;
    this.localStream?.getTracks().forEach((t) => t.stop());
    this.pc?.close();
    return duration;
  }

  async uploadRecording() {
    if (!this.chunks.length) return;
    const blob = new Blob(this.chunks, { type: this.chunks[0].type });
    try {
      await fetch(`/rtc/${encodeURIComponent(this.room)}/recording`, {
        method: 'POST',
        headers: { 'content-type': blob.type || 'application/octet-stream' },
        body: blob,
      });
    } catch { /* the call record simply shows no recording */ }
    this.chunks = [];
  }

  send(message) {
    // Fire and forget: a lost signalling message is recovered by the peer's next poll.
    fetch(`/rtc/${encodeURIComponent(this.room)}/signal`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ from: this.role, message }),
    }).catch(() => {});
  }
}

window.AsktrixCall = AsktrixCall;
