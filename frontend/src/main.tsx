import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';
import './styles/globals.css';
import './styles/ui.css';

// /lab/walkthrough opens the real app in an iframe with `?__demo=1`; the pages then talk to an in-memory API.
async function boot() {
  if (new URLSearchParams(location.search).has('__demo')) {
    (await import('./demo/mockApi')).installMockApi();
  }
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </StrictMode>
  );
}

void boot();
