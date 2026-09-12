import { api, type Project } from './api';

let cachedPrompt: string | null = null;

async function getPromptTemplate(): Promise<string> {
  if (cachedPrompt) return cachedPrompt;
  cachedPrompt = await api.get<string>('/api/tools/context-agent/prompt');
  return cachedPrompt;
}

// Builds the personalized clipboard payload: a short header naming the project
// (so the pasted prompt doesn't waste a stage figuring out what it's looking at)
// followed by the stage-by-stage instructions template.
export async function buildCopyForLlmText(project: Project): Promise<string> {
  const template = await getPromptTemplate();
  const repoLine = project.githubUrl ? ` (repo: ${project.githubUrl})` : '';
  const header =
    `You're filling out the AnvilCV project "${project.name}"${repoLine}. ` +
    `Its name and short description are already set in AnvilCV — don't spend ` +
    `effort re-deriving those two fields.\n\n`;
  return header + template;
}
