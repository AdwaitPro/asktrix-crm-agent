'use strict';

/**
 * Asktrix Admin Console (§25–§27).
 *
 * Plain ES modules-free JavaScript on purpose: the console is served by the CRM itself, has no build
 * step, and must keep working when someone clones the repo years from now.
 *
 * The one rule that carries over from the mobile app: **customer contact details render masked**.
 * Revealing them is a deliberate, audited action, not a default.
 */

const API = '';
let token = sessionStorage.getItem('asktrix.admin.token');
let currentView = 'overview';
let employees = [];
let selectedEmployee = null;

// ─────────────────────────────  Helpers  ─────────────────────────────

const $ = (sel) => document.querySelector(sel);
const el = (tag, cls, text) => {
  const node = document.createElement(tag);
  if (cls) node.className = cls;
  if (text !== undefined) node.textContent = text;
  return node;
};

/** Escapes text before it is interpolated into markup. */
const esc = (value) =>
  String(value ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

async function api(path, options = {}) {
  const response = await fetch(API + path, {
    ...options,
    headers: {
      'content-type': 'application/json',
      ...(token ? { authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    },
  });
  if (response.status === 401 || response.status === 403) {
    signOut();
    throw new Error('unauthorised');
  }
  if (!response.ok) throw new Error(`${path} → ${response.status}`);
  return response.status === 204 ? null : response.json();
}

const fmtTime = (iso) =>
  iso ? new Date(iso).toLocaleString('en-IN', {
    timeZone: 'Asia/Kolkata', day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit',
  }) : '—';

const fmtClock = (iso) =>
  iso ? new Date(iso).toLocaleTimeString('en-IN', {
    timeZone: 'Asia/Kolkata', hour: '2-digit', minute: '2-digit',
  }) : '—';

function fmtDuration(seconds) {
  const s = Number(seconds) || 0;
  if (s === 0) return '—';
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  return h > 0 ? `${h}h ${m}m` : `${m}m ${s % 60}s`;
}

/** Maps a process status onto the same semantic tones the Android app uses. */
function statusTag(status) {
  const tones = {
    COMPLETED: 'good', PAYMENT_RECEIVED: 'good', DOCUMENTS_RECEIVED: 'good',
    DOCUMENTS_PENDING: 'warn', PAYMENT_PENDING: 'warn',
    CLIENT_NOT_RESPONDING: 'bad',
    WAITING_GOVERNMENT_APPROVAL: 'pending', CALLBACK_SCHEDULED: 'pending',
    NEW: 'neutral',
  };
  const label = String(status || '').replace(/_/g, ' ').toLowerCase();
  return `<span class="tag tag--${tones[status] || 'neutral'}">${esc(label)}</span>`;
}

// ─────────────────────────────  Auth  ─────────────────────────────

$('#login-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const error = $('#login-error');
  error.hidden = true;
  try {
    const body = JSON.stringify({
      employeeCode: $('#login-code').value.trim(),
      password: $('#login-password').value,
    });
    const response = await fetch(API + '/admin/login', {
      method: 'POST', headers: { 'content-type': 'application/json' }, body,
    });
    if (!response.ok) throw new Error('Incorrect code or password, or this account is not a team leader.');
    const session = await response.json();
    token = session.accessToken;
    sessionStorage.setItem('asktrix.admin.token', token);
    sessionStorage.setItem('asktrix.admin.name', session.admin.name);
    sessionStorage.setItem('asktrix.admin.role', session.admin.role);
    enterConsole();
  } catch (e) {
    error.textContent = e.message;
    error.hidden = false;
  }
});

function signOut() {
  token = null;
  sessionStorage.clear();
  $('#app').hidden = true;
  $('#login').hidden = false;
}

$('#signout').addEventListener('click', signOut);

function enterConsole() {
  $('#login').hidden = true;
  $('#app').hidden = false;
  $('#who-name').textContent = sessionStorage.getItem('asktrix.admin.name') || '—';
  $('#who-role').textContent = (sessionStorage.getItem('asktrix.admin.role') || '').replace(/_/g, ' ');
  render();
}

// ─────────────────────────────  Navigation  ─────────────────────────────

document.querySelectorAll('.nav__item').forEach((button) => {
  button.addEventListener('click', () => {
    document.querySelectorAll('.nav__item').forEach((b) => b.classList.remove('is-active'));
    button.classList.add('is-active');
    currentView = button.dataset.view;
    $('#view-title').textContent = button.textContent;
    document.querySelectorAll('.view').forEach((v) => { v.hidden = true; });
    $(`#view-${currentView}`).hidden = false;
    render();
  });
});

$('#refresh').addEventListener('click', render);

// Auto-refresh the live views. Long enough not to hammer the CRM during a demo.
setInterval(() => {
  if (token && ['overview', 'employees'].includes(currentView)) render();
}, 30_000);

// ─────────────────────────────  Views  ─────────────────────────────

async function render() {
  if (!token) return;
  const views = { overview, employeesView, tracking, calls, clients, devices, audit };
  const fn = { overview, employees: employeesView, tracking, calls, clients, devices, audit }[currentView];
  try {
    await fn();
  } catch (e) {
    if (e.message !== 'unauthorised') console.error(e);
  }
}

async function overview() {
  const s = await api('/admin/overview');
  const connectRate = Number(s.calls_today) > 0
    ? Math.round((Number(s.calls_connected_today) / Number(s.calls_today)) * 100)
    : 0;

  $('#view-overview').innerHTML = `
    <div class="stats">
      ${stat('Checked in today', s.checked_in_today, `of ${s.employees} employees`, 'accent')}
      ${stat('Calls today', s.calls_today, `${connectRate}% connected`, '')}
      ${stat('Talk time today', fmtDuration(s.talk_seconds_today), 'across all agents', '')}
      ${stat('Follow-ups due', s.follow_ups_due, 'across all clients', Number(s.follow_ups_due) > 0 ? 'warn' : 'good')}
    </div>
    <div class="stats">
      ${stat('Active clients', s.clients, `${s.completed} completed`, '')}
      ${stat('Recordings held', s.recordings, 'stored in the CRM', '')}
      ${stat('GPS points today', s.pings_today, 'working hours only', '')}
      ${stat('Devices', s.devices,
        Number(s.devices_noncompliant) > 0 ? `${s.devices_noncompliant} non-compliant` : 'all compliant',
        Number(s.devices_noncompliant) > 0 ? 'bad' : 'good')}
    </div>

    <div class="card">
      <div class="card__head">
        <div>
          <div class="card__title">Team today</div>
          <div class="card__sub">Attendance, activity and last known position</div>
        </div>
      </div>
      <div class="table-wrap" id="overview-team"></div>
    </div>`;

  employees = (await api('/admin/employees')).items;
  $('#overview-team').innerHTML = employeeTable(employees);
}

function stat(label, value, meta, tone) {
  return `<div class="stat ${tone ? `stat--${tone}` : ''}">
    <div class="stat__label">${esc(label)}</div>
    <div class="stat__value">${esc(value)}</div>
    <div class="stat__meta">${esc(meta)}</div>
  </div>`;
}

function employeeTable(items) {
  if (!items.length) return '<div class="empty">No employees.</div>';
  return `<table>
    <thead><tr>
      <th>Employee</th><th>Status</th><th>Checked in</th><th>Clients</th>
      <th>Calls today</th><th>Talk time</th><th>Last seen</th><th>Battery</th>
    </tr></thead>
    <tbody>${items.map((e) => {
      const on = e.last_attendance === 'CHECK_IN';
      const loc = e.last_location;
      return `<tr>
        <td><div class="strong">${esc(e.display_name)}</div>
            <div class="dim mono">${esc(e.employee_code)} · ${esc(String(e.role).replace(/_/g, ' ').toLowerCase())}</div></td>
        <td>${on ? '<span class="tag tag--good">on duty</span>'
                 : '<span class="tag tag--neutral">off duty</span>'}</td>
        <td class="num dim">${fmtClock(e.check_in_at)}</td>
        <td class="num">${esc(e.assigned)}</td>
        <td class="num">${esc(e.calls_today)}</td>
        <td class="num dim">${fmtDuration(e.talk_seconds_today)}</td>
        <td class="dim">${loc ? fmtTime(loc.at) : '—'}</td>
        <td class="num dim">${loc && loc.battery != null ? `${esc(loc.battery)}%` : '—'}</td>
      </tr>`;
    }).join('')}</tbody></table>`;
}

async function employeesView() {
  employees = (await api('/admin/employees')).items;
  $('#view-employees').innerHTML = `
    <div class="card">
      <div class="card__head">
        <div>
          <div class="card__title">Employees</div>
          <div class="card__sub">Live status and today's productivity (§25–§27)</div>
        </div>
      </div>
      <div class="table-wrap">${employeeTable(employees)}</div>
    </div>`;
}

async function tracking() {
  if (!employees.length) employees = (await api('/admin/employees')).items;
  if (!selectedEmployee && employees.length) selectedEmployee = employees[0].employee_id;

  $('#view-tracking').innerHTML = `
    <div class="controls">
      <label class="field">
        <span class="field__label">Employee</span>
        <select id="track-emp">
          ${employees.map((e) => `<option value="${esc(e.employee_id)}"
            ${e.employee_id === selectedEmployee ? 'selected' : ''}>${esc(e.display_name)}</option>`).join('')}
        </select>
      </label>
    </div>
    <div class="card"><div class="card__head">
        <div>
          <div class="card__title">Location trace</div>
          <div class="card__sub">Sampled every 10 minutes, working hours only (§10)</div>
        </div>
      </div>
      <div class="card__body"><div class="trace">
        <div class="trace__plot" id="trace-plot"></div>
        <div class="trace__list" id="trace-list"></div>
      </div></div>
    </div>
    <div class="card"><div class="card__head">
        <div><div class="card__title">Attendance history</div>
        <div class="card__sub">Check-in and check-out with position (§11)</div></div>
      </div>
      <div class="table-wrap" id="attendance-table"></div>
    </div>`;

  $('#track-emp').addEventListener('change', (e) => {
    selectedEmployee = e.target.value;
    tracking();
  });

  const [locations, att] = await Promise.all([
    api(`/admin/employees/${selectedEmployee}/locations`),
    api(`/admin/employees/${selectedEmployee}/attendance`),
  ]);

  drawTrace(locations.items);

  $('#trace-list').innerHTML = locations.items.slice(0, 40).map((p) => `
    <div class="trace__row">
      <span class="mono">${Number(p.latitude).toFixed(4)}, ${Number(p.longitude).toFixed(4)}</span>
      <span class="dim">${fmtTime(p.sampled_at)}</span>
    </div>`).join('') || '<div class="empty">No location data.</div>';

  $('#attendance-table').innerHTML = att.items.length ? `<table>
    <thead><tr><th>Type</th><th>Time (IST)</th><th>Recorded by server</th><th>Position</th><th>Photo</th></tr></thead>
    <tbody>${att.items.map((a) => `<tr>
      <td>${a.kind === 'CHECK_IN' ? '<span class="tag tag--good">check in</span>'
                                  : '<span class="tag tag--neutral">check out</span>'}</td>
      <td class="num">${fmtTime(a.occurred_at)}</td>
      <td class="num dim">${fmtTime(a.recorded_at)}</td>
      <td class="mono">${Number(a.latitude).toFixed(4)}, ${Number(a.longitude).toFixed(4)}</td>
      <td>${a.photo_uploaded ? '<span class="tag tag--good">yes</span>' : '<span class="dim">—</span>'}</td>
    </tr>`).join('')}</tbody></table>` : '<div class="empty">No attendance records.</div>';
}

/**
 * Renders the location trace on a real map.
 *
 * MapLibre GL with OpenFreeMap tiles: no API key, no registration, no request limit, and commercial
 * use is permitted. That keeps the console genuinely free to run.
 *
 * If the map engine or the tile server is unreachable, this falls back to an SVG plot of the same
 * coordinates rather than showing an empty box. A demo should not depend on a venue's Wi-Fi.
 */
let mapInstance = null;
const MAP_LOAD_TIMEOUT_MS = 6000;

function drawTrace(points) {
  const host = $('#trace-plot');
  if (!points.length) {
    host.innerHTML = '<div class="empty">No location data.</div>';
    return;
  }

  const ordered = [...points].reverse();
  const coords = ordered.map((p) => [Number(p.longitude), Number(p.latitude)]);

  if (!window.maplibregl) {
    drawTraceFallback(host, ordered);
    return;
  }

  host.innerHTML = '<div id="map" style="width:100%;height:420px;border-radius:12px"></div>';

  const bounds = coords.reduce(
    (acc, c) => [
      [Math.min(acc[0][0], c[0]), Math.min(acc[0][1], c[1])],
      [Math.max(acc[1][0], c[0]), Math.max(acc[1][1], c[1])],
    ],
    [[...coords[0]], [...coords[0]]],
  );

  const dark = window.matchMedia('(prefers-color-scheme: dark)').matches;

  try {
    if (mapInstance) mapInstance.remove();
    mapInstance = new window.maplibregl.Map({
      container: 'map',
      style: dark
        ? 'https://tiles.openfreemap.org/styles/dark'
        : 'https://tiles.openfreemap.org/styles/positron',
      bounds,
      fitBoundsOptions: { padding: 56, maxZoom: 15 },
      attributionControl: { compact: true },
    });

    mapInstance.addControl(new window.maplibregl.NavigationControl({ showCompass: false }), 'top-right');
    mapInstance.addControl(new window.maplibregl.ScaleControl({ maxWidth: 110, unit: 'metric' }));

    const addLayers = () => {
      if (mapInstance.getSource('route')) return;
      mapInstance.addSource('route', {
        type: 'geojson',
        data: { type: 'Feature', geometry: { type: 'LineString', coordinates: coords } },
      });

      // A wide translucent casing under a solid line keeps the route legible over both light
      // streets and dark parkland.
      mapInstance.addLayer({
        id: 'route-casing',
        type: 'line',
        source: 'route',
        layout: { 'line-cap': 'round', 'line-join': 'round' },
        paint: { 'line-color': '#0284C7', 'line-width': 9, 'line-opacity': 0.22 },
      });
      mapInstance.addLayer({
        id: 'route-line',
        type: 'line',
        source: 'route',
        layout: { 'line-cap': 'round', 'line-join': 'round' },
        paint: { 'line-color': '#38BDF8', 'line-width': 3.2 },
      });

      mapInstance.addSource('samples', {
        type: 'geojson',
        data: {
          type: 'FeatureCollection',
          features: ordered.slice(1, -1).map((p) => ({
            type: 'Feature',
            geometry: { type: 'Point', coordinates: [Number(p.longitude), Number(p.latitude)] },
            properties: { at: fmtTime(p.sampled_at), accuracy: Math.round(Number(p.accuracy_metres) || 0) },
          })),
        },
      });
      mapInstance.addLayer({
        id: 'sample-dots',
        type: 'circle',
        source: 'samples',
        paint: {
          'circle-radius': 4,
          'circle-color': '#38BDF8',
          'circle-stroke-width': 1.5,
          'circle-stroke-color': dark ? '#0B1220' : '#FFFFFF',
        },
      });

      mapInstance.on('click', 'sample-dots', (event) => {
        const f = event.features[0];
        new window.maplibregl.Popup({ closeButton: false, offset: 10 })
          .setLngLat(f.geometry.coordinates)
          .setHTML(`<div style="font:12px system-ui"><strong>${esc(f.properties.at)}</strong><br>
                    accurate to ${esc(f.properties.accuracy)} m</div>`)
          .addTo(mapInstance);
      });
      mapInstance.on('mouseenter', 'sample-dots', () => {
        mapInstance.getCanvas().style.cursor = 'pointer';
      });
      mapInstance.on('mouseleave', 'sample-dots', () => {
        mapInstance.getCanvas().style.cursor = '';
      });

      addTraceMarker(coords[0], '#F59E0B', 'Start of day', fmtTime(ordered[0].sampled_at));
      addTraceMarker(
        coords[coords.length - 1], '#10B981', 'Latest position',
        fmtTime(ordered[ordered.length - 1].sampled_at),
      );
    };

    // Keyed on the style being ready rather than the map's 'load' event. Some browsers with
    // software WebGL render tiles perfectly but never emit 'load', and gating on it there throws
    // away a working map.
    if (mapInstance.isStyleLoaded()) addLayers();
    else mapInstance.once('styledata', addLayers);

    // Fall back only when the style itself cannot be fetched, which is what an unreachable tile
    // server actually looks like. Benign per-tile errors must not tear down a working map.
    let styleArrived = false;
    mapInstance.on('styledata', () => { styleArrived = true; });
    setTimeout(() => {
      if (!styleArrived) drawTraceFallback(host, ordered);
    }, MAP_LOAD_TIMEOUT_MS);
  } catch (e) {
    drawTraceFallback(host, ordered);
  }

  const summary = document.createElement('div');
  summary.className = 'dim';
  summary.style.cssText = 'font-size:12px;margin-top:10px;text-align:center';
  summary.textContent =
    `${ordered.length} points, ${fmtTime(ordered[0].sampled_at)} to `
    + `${fmtTime(ordered[ordered.length - 1].sampled_at)}`;
  host.appendChild(summary);
}

function addTraceMarker(lngLat, color, title, when) {
  const pin = document.createElement('div');
  pin.style.cssText = `width:16px;height:16px;border-radius:50%;background:${color};
    border:3px solid #fff;box-shadow:0 2px 8px rgba(0,0,0,.4);cursor:pointer`;
  new window.maplibregl.Marker({ element: pin })
    .setLngLat(lngLat)
    .setPopup(new window.maplibregl.Popup({ closeButton: false, offset: 14 })
      .setHTML(`<div style="font:12px system-ui"><strong>${esc(title)}</strong><br>${esc(when)}</div>`))
    .addTo(mapInstance);
}

/** Coordinate plot used when the map engine or tile server is unavailable. */
function drawTraceFallback(host, ordered) {
  const lats = ordered.map((p) => Number(p.latitude));
  const lngs = ordered.map((p) => Number(p.longitude));
  const pad = 0.0015;
  const minLat = Math.min(...lats) - pad, maxLat = Math.max(...lats) + pad;
  const minLng = Math.min(...lngs) - pad, maxLng = Math.max(...lngs) + pad;
  const x = (lng) => ((lng - minLng) / (maxLng - minLng || 1)) * 100;
  const y = (lat) => 100 - ((lat - minLat) / (maxLat - minLat || 1)) * 100;

  const path = ordered.map((p, i) =>
    `${i === 0 ? 'M' : 'L'}${x(Number(p.longitude)).toFixed(2)},${y(Number(p.latitude)).toFixed(2)}`).join(' ');

  host.innerHTML = `
    <svg viewBox="0 0 100 100" preserveAspectRatio="xMidYMid meet"
         style="width:100%;height:380px;display:block">
      <path d="${path}" fill="none" stroke="#38BDF8" stroke-width="2"
            vector-effect="non-scaling-stroke" stroke-linejoin="round" stroke-linecap="round"/>
    </svg>
    <div class="dim" style="font-size:12px;margin-top:8px;text-align:center">
      Map tiles unavailable, showing the plotted route
    </div>`;
}

async function calls() {
  const data = await api('/admin/calls');
  $('#view-calls').innerHTML = `
    <div class="card">
      <div class="card__head">
        <div>
          <div class="card__title">Call records</div>
          <div class="card__sub">
            Every call placed through the CRM (§6, §7). Recordings are held server-side and are never
            downloaded to a handset.
          </div>
        </div>
      </div>
      <div class="table-wrap">${data.items.length ? `<table>
        <thead><tr><th>Client</th><th>Agent</th><th>Outcome</th><th>Duration</th>
                   <th>When (IST)</th><th>Recording</th><th>Device</th></tr></thead>
        <tbody>${data.items.map((c) => `<tr>
          <td><div class="strong">${esc(c.client_name)}</div><div class="dim mono">${esc(c.client_id)}</div></td>
          <td class="dim">${esc(c.employee_name)}</td>
          <td>${c.state === 'COMPLETED' ? '<span class="tag tag--good">connected</span>'
              : c.state === 'NO_ANSWER' ? '<span class="tag tag--warn">no answer</span>'
              : c.state === 'BUSY' ? '<span class="tag tag--warn">busy</span>'
              : `<span class="tag tag--bad">${esc(String(c.state).toLowerCase())}</span>`}</td>
          <td class="num">${fmtDuration(c.duration_seconds)}</td>
          <td class="num dim">${fmtTime(c.started_at)}</td>
          <td>${c.recording_available
              ? `<button class="btn btn--ghost btn--tiny" data-play="${esc(c.call_record_id)}">Play</button>`
              : '<span class="dim">—</span>'}</td>
          <td class="mono">${esc(String(c.device_id || '—').slice(0, 14))}</td>
        </tr>`).join('')}</tbody></table>` : '<div class="empty">No calls yet.</div>'}
      </div>
    </div>
    <audio id="player" controls hidden style="width:100%;margin-top:16px"></audio>`;

  document.querySelectorAll('[data-play]').forEach((button) => {
    button.addEventListener('click', () => playRecording(button.dataset.play));
  });
}

/**
 * Plays a recording through an authenticated fetch.
 *
 * The <audio> src cannot carry an Authorization header, so the bytes are fetched into a blob URL.
 * That also means no unauthenticated URL to the audio ever exists, even briefly.
 */
async function playRecording(callRecordId) {
  const player = $('#player');
  try {
    const response = await fetch(`/admin/recordings/${callRecordId}`, {
      headers: { authorization: `Bearer ${token}` },
    });
    if (!response.ok) throw new Error('recording unavailable');
    if (player.dataset.url) URL.revokeObjectURL(player.dataset.url);
    const url = URL.createObjectURL(await response.blob());
    player.dataset.url = url;
    player.src = url;
    player.hidden = false;
    player.play();
  } catch (e) {
    console.error(e);
  }
}

async function clients() {
  const data = await api('/admin/clients');
  $('#view-clients').innerHTML = `
    <div class="card">
      <div class="card__head">
        <div>
          <div class="card__title">Client pipeline</div>
          <div class="card__sub">
            Contact details are masked here exactly as they are on the handsets (§4). Revealing them
            is recorded in the audit log.
          </div>
        </div>
      </div>
      <div class="table-wrap"><table>
        <thead><tr><th>Client</th><th>Assigned to</th><th>Status</th><th>Payment</th>
                   <th>Docs</th><th>Contact</th><th></th></tr></thead>
        <tbody>${data.items.map((c) => `<tr id="row-${esc(c.clientId)}">
          <td><div class="strong">${esc(c.name)}</div>
              <div class="dim mono">${esc(c.clientId)} · ${esc(c.serviceId || '')}</div></td>
          <td class="dim">${esc(c.assignedTo || 'Unassigned')}</td>
          <td>${statusTag(c.processStatus)}</td>
          <td class="dim">${esc(String(c.paymentStatus).replace(/_/g, ' ').toLowerCase())}</td>
          <td class="num">${c.documentsPending > 0
              ? `<span class="tag tag--warn">${esc(c.documentsPending)}</span>` : '<span class="dim">0</span>'}</td>
          <td class="masked" id="contact-${esc(c.clientId)}">${esc(c.phoneMasked)}<br>
              <span class="dim">${esc(c.emailMasked)}</span></td>
          <td><button class="btn btn--ghost btn--tiny"
                data-reveal="${esc(c.clientId)}">Reveal</button></td>
        </tr>`).join('')}</tbody>
      </table></div>
    </div>`;

  document.querySelectorAll('[data-reveal]').forEach((button) => {
    button.addEventListener('click', () => promptReveal(button.dataset.reveal));
  });
}

/** The only path to an unmasked value, and it always writes an audit entry. */
function promptReveal(clientId) {
  const dialog = $('#reveal-dialog');
  $('#reveal-reason').value = '';
  dialog.showModal();
  dialog.addEventListener('close', async function once() {
    dialog.removeEventListener('close', once);
    if (dialog.returnValue !== 'confirm') return;
    try {
      const revealed = await api(`/admin/clients/${clientId}/reveal`, {
        method: 'POST',
        body: JSON.stringify({ reason: $('#reveal-reason').value || 'not stated' }),
      });
      const cell = $(`#contact-${clientId}`);
      cell.className = 'revealed';
      cell.innerHTML = `${esc(revealed.phone)}<br><span>${esc(revealed.email)}</span>
        <div class="dim" style="font-size:11px;margin-top:4px">recorded in audit log</div>`;
    } catch (e) {
      console.error(e);
    }
  });
}

async function devices() {
  const data = await api('/admin/devices');
  $('#view-devices').innerHTML = `
    <div class="card">
      <div class="card__head">
        <div>
          <div class="card__title">Enrolled devices</div>
          <div class="card__sub">
            Integrity and management status reported by each handset (§14–§20). Restrictions
            themselves are enforced by EMM policy, not by the app.
          </div>
        </div>
      </div>
      <div class="table-wrap">${data.items.length ? `<table>
        <thead><tr><th>Device</th><th>Employee</th><th>Android</th><th>App</th>
                   <th>Compliance</th><th>Push</th><th>Last seen</th></tr></thead>
        <tbody>${data.items.map((d) => `<tr>
          <td><div class="strong">${esc(d.manufacturer)} ${esc(d.model)}</div>
              <div class="dim mono">${esc(String(d.device_id).slice(0, 18))}…</div></td>
          <td class="dim">${esc(d.employee_name || '—')}</td>
          <td class="dim">${esc(d.os_version || '—')}</td>
          <td class="dim mono">${esc(d.app_version || '—')}</td>
          <td>${d.compliant ? '<span class="tag tag--good">compliant</span>'
                            : `<span class="tag tag--bad">${esc(d.last_verdict || 'failed')}</span>`}</td>
          <td>${d.push_registered ? '<span class="tag tag--good">registered</span>'
                                  : '<span class="tag tag--neutral">none</span>'}</td>
          <td class="dim">${fmtTime(d.last_seen_at)}</td>
        </tr>`).join('')}</tbody></table>` : '<div class="empty">No devices enrolled.</div>'}
      </div>
    </div>`;
}

async function audit() {
  const data = await api('/admin/audit');
  $('#view-audit').innerHTML = `
    <div class="card">
      <div class="card__head">
        <div>
          <div class="card__title">PII access log</div>
          <div class="card__sub">
            Every time an administrator unmasked a customer's real contact details. This is what a
            DPDP audit asks for.
          </div>
        </div>
      </div>
      <div class="table-wrap">${data.items.length ? `<table>
        <thead><tr><th>Administrator</th><th>Client</th><th>Reason</th><th>When (IST)</th></tr></thead>
        <tbody>${data.items.map((a) => `<tr>
          <td class="strong">${esc(a.admin_name)}</td>
          <td class="mono">${esc(a.client_id)}</td>
          <td class="dim">${esc(a.reason)}</td>
          <td class="num dim">${fmtTime(a.accessed_at)}</td>
        </tr>`).join('')}</tbody></table>`
        : '<div class="empty">No contact details have been revealed. That is the healthy state.</div>'}
      </div>
    </div>`;
}

// ─────────────────────────────  Boot  ─────────────────────────────

if (token) enterConsole();
