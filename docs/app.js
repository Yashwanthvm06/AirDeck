document.addEventListener('DOMContentLoaded', () => {
  // Tabs switching
  const tabButtons = document.querySelectorAll('.tab-btn');
  const tabContents = document.querySelectorAll('.tab-content');

  tabButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      tabButtons.forEach(b => b.classList.remove('active'));
      tabContents.forEach(c => c.classList.remove('active'));

      btn.classList.add('active');
      const target = document.getElementById(btn.dataset.tab);
      if (target) {
        target.classList.add('active');
      }
    });
  });

  // Trackpad simulation cursor movement
  const trackpad = document.getElementById('demo-trackpad');
  const cursor = document.getElementById('demo-cursor');

  if (trackpad && cursor) {
    trackpad.addEventListener('mousemove', (e) => {
      const rect = trackpad.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      cursor.style.left = `${x}px`;
      cursor.style.top = `${y}px`;
    });

    trackpad.addEventListener('mousedown', () => {
      cursor.style.transform = 'translate(-50%, -50%) scale(0.85)';
      cursor.style.borderColor = '#10b981';
      cursor.style.backgroundColor = 'rgba(16, 185, 129, 0.3)';
    });

    trackpad.addEventListener('mouseup', () => {
      cursor.style.transform = 'translate(-50%, -50%) scale(1)';
      cursor.style.borderColor = '#06b6d4';
      cursor.style.backgroundColor = 'rgba(6, 182, 212, 0.2)';
    });
  }

  // Interactive buttons sound/haptic feedback simulation
  const interactiveBtns = document.querySelectorAll('.touch-btn, .abxy-btn, .dpad-btn, .media-btn');
  interactiveBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      btn.style.transform = 'scale(0.92)';
      setTimeout(() => {
        btn.style.transform = '';
      }, 120);
    });
  });

  // FAQ Accordion
  const faqItems = document.querySelectorAll('.faq-item');
  faqItems.forEach(item => {
    const question = item.querySelector('.faq-question');
    question.addEventListener('click', () => {
      const isOpen = item.classList.contains('open');
      faqItems.forEach(i => i.classList.remove('open'));
      if (!isOpen) {
        item.classList.add('open');
      }
    });
  });
});
