import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { RepoTab, repoActionBlocked, REPO_WARNING_TEXT } from './RepoTab';
import type { Project } from '../../lib/api';

const project = (kind: Project['kind']): Project => ({
  id: 'p1', kind, name: 'Role', description: '', createdAt: '',
} as Project);

describe('repoActionBlocked', () => {
  it('never blocks a project', () => {
    expect(repoActionBlocked('PROJECT', false)).toBe(false);
  });
  it('blocks an experience until the warning is acknowledged', () => {
    expect(repoActionBlocked('EXPERIENCE', false)).toBe(true);
    expect(repoActionBlocked('EXPERIENCE', true)).toBe(false);
  });
});

describe('RepoTab warning', () => {
  it('shows the warning and an acknowledgement box for an experience', () => {
    const html = renderToStaticMarkup(<RepoTab id="p1" project={project('EXPERIENCE')} onChanged={() => {}} />);
    expect(html).toContain(REPO_WARNING_TEXT.replace(/'/g, '&#x27;'));
    expect(html).toContain('type="checkbox"');
  });
  it('shows no warning for a project', () => {
    const html = renderToStaticMarkup(<RepoTab id="p1" project={project('PROJECT')} onChanged={() => {}} />);
    expect(html).not.toContain('Read before running the repo map');
  });
});
