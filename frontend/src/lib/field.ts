import { useEffect, useState } from 'react';
import { api, CATEGORIES } from './api';

export interface Lens { slug: string; label: string; blurb: string }
export interface FieldPreset { field: string; name: string; lenses: Lens[]; skillLabels: string[] }
interface FieldView { field: string; preset: FieldPreset; available: FieldPreset[] }

/** What the app shows until /api/field answers (and for a failed load): the software field. */
const FALLBACK: FieldView = {
  field: 'software',
  preset: { field: 'software', name: 'Software', lenses: CATEGORIES, skillLabels: [] },
  available: [],
};

let cached: FieldView | null = null;
let inflight: Promise<FieldView> | null = null;
const listeners = new Set<(v: FieldView) => void>();

function publish(v: FieldView) {
  cached = v;
  listeners.forEach((l) => l(v));
}

function load(): Promise<FieldView> {
  inflight ??= api.get<FieldView>('/api/field')
    .then((v) => { publish(v); return v; })
    .finally(() => { inflight = null; });
  return inflight;
}

/** Forget the cache on sign-out so the next user does not inherit this user's field. */
export function resetField() {
  cached = null;
}

/** The signed-in user's field: its lenses and skill labels, plus a way to change it. */
export function useField() {
  const [view, setView] = useState<FieldView>(cached ?? FALLBACK);
  useEffect(() => {
    listeners.add(setView);
    if (cached) setView(cached);
    else load().catch(() => { /* keep the software fallback */ });
    return () => { listeners.delete(setView); };
  }, []);

  const setField = async (field: string) => publish(await api.put<FieldView>('/api/field', { field }));
  return { field: view.field, lenses: view.preset.lenses, skillLabels: view.preset.skillLabels, available: view.available, setField };
}

/** Set the field right after sign-up, before any page has loaded it. */
export async function saveField(field: string) {
  publish(await api.put<FieldView>('/api/field', { field }));
}
