/** Drives the real app inside an iframe: a visible cursor that moves, clicks and types into the page's own controls. */

export class Stopped extends Error {}

export class Driver {
  paused = false;
  stopped = false;
  speed = 1;
  private gate: (() => void) | null = null;

  constructor(private frame: HTMLIFrameElement) {}

  get doc() { return this.frame.contentDocument!; }
  get win() { return this.frame.contentWindow as Window & typeof globalThis; }

  pause() { this.paused = true; }
  resume() { this.paused = false; this.gate?.(); this.gate = null; }
  stop() { this.stopped = true; this.resume(); }

  private async tick() {
    if (this.stopped) throw new Stopped();
    if (this.paused) await new Promise<void>(r => { this.gate = r; });
    if (this.stopped) throw new Stopped();
  }

  async sleep(ms: number) {
    await this.tick();
    await new Promise(r => setTimeout(r, ms / this.speed));
    await this.tick();
  }

  /** Waits for an element that exists and has a box, so a hidden or not-yet-rendered one is never clicked. */
  async find(target: string | (() => Element | null | undefined), timeout = 12000): Promise<HTMLElement> {
    const start = Date.now();
    for (;;) {
      await this.tick();
      const el = (typeof target === 'string' ? this.doc.querySelector(target) : target()) as HTMLElement | null | undefined;
      if (el && (el.getClientRects().length > 0)) return el;
      if (Date.now() - start > timeout) throw new Error(`Not found: ${typeof target === 'string' ? target : 'element'}`);
      await new Promise(r => setTimeout(r, 60));
    }
  }

  byText(sel: string, re: RegExp) {
    return () => [...this.doc.querySelectorAll<HTMLElement>(sel)].find(e => re.test(e.textContent ?? '') && !(e as HTMLButtonElement).disabled);
  }

  /** The guide's tip for the current step, once it matches `title`. A tip that never shows is a guide bug, so this throws. */
  async readTip(title: RegExp, ms = 1700) {
    const tip = await this.find(() => [...this.doc.querySelectorAll<HTMLElement>('.ob-tip, .ob-corner')].find(e => title.test(e.textContent ?? '')), 15000);
    await this.moveTo(tip.querySelector<HTMLElement>('strong') ?? tip);
    await this.sleep(ms);
    return tip;
  }

  /** Waits for an element to disappear; a guide that never finishes is a bug worth failing on. */
  async gone(sel: string, timeout = 12000) {
    const start = Date.now();
    while (this.doc.querySelector(sel)) {
      await this.tick();
      if (Date.now() - start > timeout) throw new Error(`Still there: ${sel}`);
      await new Promise(r => setTimeout(r, 80));
    }
  }

  /** Follow the tip: its "Take me there" button. */
  async takeMeThere(title: RegExp) {
    await this.readTip(title);
    await this.click('.ob-tip__go');
  }

  private cursor() {
    let c = this.doc.getElementById('__demo-cursor');
    if (!c) {
      c = this.doc.createElement('div');
      c.id = '__demo-cursor';
      c.style.cssText = 'position:fixed;left:0;top:0;width:26px;height:26px;z-index:2147483647;pointer-events:none;transform:translate(60vw,90vh);filter:drop-shadow(0 2px 3px rgba(0,0,0,.35))';
      c.innerHTML = '<svg width="26" height="26" viewBox="0 0 24 24"><path d="M3 2l7 19 3-8 8-3z" fill="#0a0a0a" stroke="#fff" stroke-width="1.6" stroke-linejoin="round"/></svg>';
      this.doc.body.appendChild(c);
    }
    return c;
  }

  async moveTo(el: HTMLElement) {
    await this.tick();
    const vr = el.getBoundingClientRect();
    if (vr.top < 90 || vr.bottom > this.win.innerHeight - 40) {
      el.scrollIntoView({ block: 'center', behavior: 'auto' });
      await new Promise(r => setTimeout(r, 120));
    }
    const r = el.getBoundingClientRect();
    const x = r.left + Math.min(r.width / 2, 60);
    const y = r.top + Math.min(r.height / 2, 18);
    const c = this.cursor();
    c.style.transition = `transform ${Math.round(650 / this.speed)}ms cubic-bezier(.23,1,.32,1)`;
    c.style.transform = `translate(${x - 4}px,${y - 3}px)`;
    await this.sleep(700);
  }

  private ripple(el: HTMLElement) {
    const c = this.cursor().getBoundingClientRect();
    const d = this.doc.createElement('div');
    d.style.cssText = `position:fixed;left:${c.left - 8}px;top:${c.top - 8}px;width:24px;height:24px;border-radius:50%;border:2px solid #0a0a0a;background:rgba(220,255,0,.5);z-index:2147483646;pointer-events:none`;
    this.doc.body.appendChild(d);
    d.animate([{ transform: 'scale(.3)', opacity: 1 }, { transform: 'scale(1.8)', opacity: 0 }], { duration: 420, easing: 'ease-out' }).onfinish = () => d.remove();
    void el;
  }

  async click(target: string | (() => Element | null | undefined)) {
    const el = await this.find(target);
    await this.moveTo(el);
    this.ripple(el);
    el.click();
    await this.sleep(450);
    return el;
  }

  /** Types through the native setter so React's controlled inputs see every keystroke. */
  async type(target: string | (() => Element | null | undefined), text: string) {
    const el = await this.find(target) as HTMLInputElement | HTMLTextAreaElement;
    await this.moveTo(el);
    this.ripple(el);
    el.focus();
    const proto = el instanceof this.win.HTMLTextAreaElement ? this.win.HTMLTextAreaElement.prototype : this.win.HTMLInputElement.prototype;
    const set = Object.getOwnPropertyDescriptor(proto, 'value')!.set!;
    const step = text.length > 120 ? 7 : 1;
    for (let i = step; ; i += step) {
      const end = Math.min(i, text.length);
      set.call(el, text.slice(0, end));
      el.dispatchEvent(new this.win.Event('input', { bubbles: true }));
      if (end >= text.length) break;
      await this.sleep(step > 1 ? 22 : 45);
    }
    await this.sleep(250);
  }
}
