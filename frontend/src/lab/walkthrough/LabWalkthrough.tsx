/* /lab/walkthrough — the real app (sign-up, onboarding, tailor) played by a scripted cursor against an in-memory API. */
import { useCallback, useEffect, useRef, useState } from 'react';
import { LabChrome } from '../LabChrome';
import { Driver, Stopped } from './driver';
import { SCENES } from './scenes';
import '../../styles/ledger.css';
import './walkthrough.css';

type Status = 'idle' | 'playing' | 'paused' | 'done' | 'error';
type Device = 'desktop' | 'phone';

const SIZE: Record<Device, { w: number; h: number }> = { desktop: { w: 1280, h: 800 }, phone: { w: 390, h: 800 } };
const ROAD_KEY = 'anvil.onboarding.road';

export function LabWalkthrough() {
  const frame = useRef<HTMLIFrameElement>(null);
  const stage = useRef<HTMLDivElement>(null);
  const driver = useRef<Driver | null>(null);
  const autoplay = useRef(false);
  const [nonce, setNonce] = useState(0);
  const [loaded, setLoaded] = useState(false);
  const [status, setStatus] = useState<Status>('idle');
  const [scene, setScene] = useState(-1);
  const [err, setErr] = useState<string | null>(null);
  const [speed, setSpeed] = useState(1);
  const [device, setDevice] = useState<Device>('desktop');
  const [scale, setScale] = useState(1);

  useEffect(() => {
    const el = stage.current;
    if (!el) return;
    const fit = () => setScale(Math.min(1, el.clientWidth / SIZE[device].w));
    fit();
    const ro = new ResizeObserver(fit);
    ro.observe(el);
    return () => ro.disconnect();
  }, [device]);

  useEffect(() => { if (driver.current) driver.current.speed = speed; }, [speed]);
  useEffect(() => () => driver.current?.stop(), []);

  const play = useCallback(async () => {
    if (!frame.current) return;
    const d = new Driver(frame.current);
    d.speed = speed;
    driver.current = d;
    setErr(null);
    setStatus('playing');
    try {
      for (let i = 0; i < SCENES.length; i++) {
        setScene(i);
        await SCENES[i].run(d);
      }
      setScene(SCENES.length);
      setStatus('done');
    } catch (e) {
      if (e instanceof Stopped) return;
      setErr(e instanceof Error ? e.message : String(e));
      setStatus('error');
    }
  }, [speed]);

  // A restart reloads the frame (fresh in-memory API); play as soon as the new page has loaded.
  useEffect(() => { if (loaded && autoplay.current) { autoplay.current = false; void play(); } }, [loaded, play]);

  const restart = () => {
    driver.current?.stop();
    try { localStorage.removeItem(ROAD_KEY); } catch { /* storage blocked */ }
    autoplay.current = true;
    setLoaded(false);
    setScene(-1);
    setStatus('idle');
    setNonce(n => n + 1);
  };

  const toggle = () => {
    const d = driver.current;
    if (status === 'playing' && d) { d.pause(); setStatus('paused'); }
    else if (status === 'paused' && d) { d.resume(); setStatus('playing'); }
    else if (status === 'idle') void play();
    else restart();
  };

  const { w, h } = SIZE[device];
  const label = { idle: '▶ Play', playing: '❚❚ Pause', paused: '▶ Resume', done: '↻ Replay', error: '↻ Retry' }[status];

  return (
    <LabChrome
      title="Walkthrough"
      note={<>The shipped app, played end to end: sign up, onboarding, tailor. Every page is the real component; only the API is faked, in memory, so nothing is saved.</>}
    >
      <div className="wt-bar">
        <button type="button" className="ap-btn ap-btn--acid" disabled={!loaded && status === 'idle'} onClick={toggle}>{label}</button>
        {status !== 'idle' && <button type="button" className="ap-btn ap-btn--ghost" onClick={restart}>Restart</button>}
        <span className="wt-seg" role="group" aria-label="Speed">
          {[1, 1.5, 2].map(s => <button key={s} type="button" aria-pressed={speed === s} onClick={() => setSpeed(s)}>{s}×</button>)}
        </span>
        <span className="wt-seg" role="group" aria-label="Device">
          {(['desktop', 'phone'] as Device[]).map(dv => <button key={dv} type="button" aria-pressed={device === dv} onClick={() => { setDevice(dv); restart(); }}>{dv}</button>)}
        </span>
      </div>

      <div className="wt-grid">
        <ol className="wt-steps">
          {SCENES.map((s, i) => (
            <li key={s.id} data-state={i < scene || status === 'done' ? 'done' : i === scene ? 'now' : 'next'}>
              <span className="wt-steps__n">{i + 1}</span>
              <strong>{s.title}</strong>
              <span>{s.caption}</span>
            </li>
          ))}
        </ol>

        <div className="wt-stage" ref={stage}>
          {err && <div className="err wt-err" role="alert">Stopped at “{SCENES[scene]?.title}”: {err}</div>}
          <div className="wt-clip" style={{ width: w * scale, height: h * scale }}>
            <iframe
              key={nonce}
              ref={frame}
              title="AnvilCV walkthrough"
              src="/register?__demo=1"
              width={w}
              height={h}
              style={{ transform: `scale(${scale})`, transformOrigin: '0 0' }}
              onLoad={() => setLoaded(true)}
            />
          </div>
        </div>
      </div>
    </LabChrome>
  );
}
