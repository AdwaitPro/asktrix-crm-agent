'use strict';

/**
 * Guide page behaviour.
 *
 * One job: keep the sidebar showing which section you are reading. Everything else on the page is
 * plain HTML, because a document that someone opens when they are already confused should not have
 * a way to fail.
 */

(function () {
  const links = [...document.querySelectorAll('.toc a')];
  const sections = links
    .map((link) => document.querySelector(link.getAttribute('href')))
    .filter(Boolean);

  if (sections.length === 0) return;

  const linkFor = new Map(sections.map((section, i) => [section, links[i]]));
  let current = null;

  function activate(section) {
    if (section === current) return;
    current = section;
    links.forEach((l) => l.classList.remove('is-active'));
    const link = linkFor.get(section);
    if (!link) return;
    link.classList.add('is-active');

    // On narrow screens the rail scrolls sideways, so the active item has to be brought into view
    // or it silently drifts off the edge and stops being a map of the page.
    if (window.matchMedia('(max-width: 900px)').matches) {
      link.scrollIntoView({ block: 'nearest', inline: 'center', behavior: 'smooth' });
    }
  }

  /**
   * Pick the last section whose top has passed the reading line, rather than trusting intersection
   * ratios. Sections here vary from a short paragraph to a long table, and ratio-based highlighting
   * makes the short ones flicker.
   */
  function update() {
    const line = window.innerHeight * 0.28;
    let found = sections[0];
    for (const section of sections) {
      if (section.getBoundingClientRect().top <= line) found = section;
    }
    // At the very bottom the last section may never cross the line, so claim it explicitly.
    const atBottom = window.innerHeight + window.scrollY >= document.body.offsetHeight - 4;
    activate(atBottom ? sections[sections.length - 1] : found);
  }

  let ticking = false;
  function onScroll() {
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(() => {
      update();
      ticking = false;
    });
  }

  window.addEventListener('scroll', onScroll, { passive: true });
  window.addEventListener('resize', onScroll, { passive: true });
  update();
})();
