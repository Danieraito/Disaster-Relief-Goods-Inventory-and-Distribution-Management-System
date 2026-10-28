// BarangayCare - shared helpers used by every page.

const NAV = [
  ['Dashboard', 'dashboard.html'],
  ['Inventory', 'inventory.html'],
  ['Disasters', 'disasters.html'],
  ['Beneficiaries', 'beneficiaries.html'],
  ['Donations', 'donations.html'],
  ['Distribution', 'distribution.html'],
  ['Reports', 'reports.html'],
  ['Audit Logs', 'audit.html'],
  ['User Administration', 'users.html', 'admin']
];

// Talks to the Java server. Sends you to the login page if you are not signed in.
async function api(path, options) {
  const response = await fetch(path, options);
  let data = null;
  try { data = await response.json(); } catch (e) { /* no JSON body */ }
  if (response.status === 401 && path !== '/api/login') {
    window.location.href = 'login.html';
    throw new Error('Please sign in.');
  }
  if (!response.ok) {
    throw new Error((data && data.error) || 'Something went wrong.');
  }
  return data;
}

function esc(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function fmtDate(iso) {
  if (!iso) return 'Not applicable';
  const d = new Date(iso + 'T00:00:00');
  if (isNaN(d)) return esc(iso);
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

function fmtNum(n) {
  return Number(n).toLocaleString('en-US');
}

function greeting() {
  const hour = new Date().getHours();
  return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
}

// Checks the login, draws the top bar, and returns the signed-in user.
async function startPage(activeTab) {
  const me = await api('/api/me');
  const tabs = NAV
    .filter(t => t[2] !== 'admin' || me.canManageUsers)
    .map(t => '<a class="tab' + (t[0] === activeTab ? ' active' : '') + '" href="' + t[1] + '">' + t[0] + '</a>')
    .join('');
  document.getElementById('shell').innerHTML =
    '<header class="topbar">' +
      '<div class="topbar-row">' +
        '<a class="brand" href="dashboard.html">BarangayCare</a>' +
        '<div class="who"><span>' + esc(me.name) + ' &middot; ' + esc(me.roleLabel) + '</span>' +
        '<button class="link-btn" id="signOut">Sign out</button></div>' +
      '</div>' +
      '<nav class="tabs">' + tabs + '</nav>' +
    '</header>';
  document.getElementById('signOut').addEventListener('click', async () => {
    try { await fetch('/api/logout', { method: 'POST' }); } catch (e) { /* ignore */ }
    window.location.href = 'login.html';
  });
  return me;
}

function pageHead(me, title, subtitle, actionHtml) {
  return '<div class="crumb">' + esc(me.workspace) + ' / ' + esc(title) + '</div>' +
    '<div class="page-head"><div><h1>' + esc(title) + '</h1><p class="sub">' + esc(subtitle) + '</p></div>' +
    (actionHtml || '') + '</div>';
}


// ---------------------------------------------------------------- shared helpers for the module pages

// Sends a form to the server. Values that are arrays become repeated fields (one per line).
function post(path, values) {
  const body = new URLSearchParams();
  Object.keys(values).forEach(key => {
    const value = values[key];
    if (Array.isArray(value)) value.forEach(v => body.append(key, v == null ? '' : v));
    else body.append(key, value == null ? '' : value);
  });
  return api(path, { method: 'POST', body: body });
}

// "2026-10-03 10:42:00" -> "3 Oct 2026 · 10:42 AM"
function fmtDateTime(text) {
  if (!text) return '—';
  const d = text instanceof Date ? text : new Date(String(text).replace(' ', 'T'));
  if (isNaN(d)) return esc(text);
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }) + ' · ' +
    d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
}

function todayIso() {
  const d = new Date();
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
}

function titleCase(text) {
  return String(text || '').toLowerCase().replace(/(^|[\s_])\w/g, m => m.toUpperCase()).replace(/_/g, ' ');
}

// Coloured status word used in tables.
function statusPill(status) {
  const map = {
    APPROVED: ['Posted', 'ok'], PENDING: ['Pending approval', 'warn'], REJECTED: ['Rejected', 'danger'], DRAFT: ['Draft', 'muted'],
    ACTIVE: ['Active', 'ok'], ONGOING: ['Active', 'ok'], CLOSED: ['Closed', 'muted'], INACTIVE: ['Inactive', 'muted']
  };
  const m = map[status] || [titleCase(status), 'muted'];
  return '<span class="pill-st ' + m[1] + '">' + esc(m[0]) + '</span>';
}

function openModal(id) { document.getElementById(id).classList.add('open'); }
function closeModal(id) { document.getElementById(id).classList.remove('open'); }

function showError(id, message) {
  const box = document.getElementById(id);
  if (!message) { box.style.display = 'none'; return; }
  box.textContent = message;
  box.style.display = 'block';
}

// Shows a success or error message at the top of the page for a few seconds.
function flash(html, kind) {
  let box = document.getElementById('flash');
  if (!box) {
    box = document.createElement('div');
    box.id = 'flash';
    const main = document.getElementById('main');
    main.parentNode.insertBefore(box, main);
  }
  box.className = 'flash banner ' + (kind || 'ok');
  box.innerHTML = '<div>' + html + '</div><button class="link-btn" aria-label="Dismiss">✕</button>';
  box.querySelector('button').onclick = () => box.remove();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

// Barangay picker shown only to Admins (everyone else always works in their own barangay).
function barangayField(me, id) {
  if (me.role !== 'ADMIN') return '';
  return '<div class="field"><label for="' + id + '">Barangay workspace</label><select id="' + id + '">' +
    me.barangays.map(b => '<option value="' + esc(b.id) + '">' + esc(b.name) + '</option>').join('') + '</select></div>';
}

// Splits a list into pages. Returns the rows for the page and a footer with Previous / Next.
function paginate(list, page, size, onChange, noun) {
  const pages = Math.max(1, Math.ceil(list.length / size));
  page = Math.min(Math.max(1, page), pages);
  const rows = list.slice((page - 1) * size, page * size);
  const footer = document.createElement('div');
  footer.className = 'pager';
  footer.innerHTML = 'Showing ' + rows.length + ' of ' + list.length + ' ' + noun + ' · Page ' + page + ' of ' + pages +
    ' <button class="link" data-p="prev"' + (page <= 1 ? ' disabled' : '') + '>Previous</button> / ' +
    '<button class="link" data-p="next"' + (page >= pages ? ' disabled' : '') + '>Next</button>';
  footer.querySelector('[data-p=prev]').onclick = () => onChange(page - 1);
  footer.querySelector('[data-p=next]').onclick = () => onChange(page + 1);
  return { rows: rows, footer: footer };
}

// Downloads rows as a CSV file (opens in Excel). The first row is the header.
function downloadCsv(filename, rows) {
  const q = v => '"' + String(v == null ? '' : v).replace(/"/g, '""') + '"';
  const text = '﻿' + rows.map(r => r.map(q).join(',')).join('\r\n');
  const link = document.createElement('a');
  link.href = URL.createObjectURL(new Blob([text], { type: 'text/csv;charset=utf-8' }));
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(link.href), 1000);
}

// "2 sack Rice, 12 bottle Water" from item lines.
function linesText(items) {
  return (items || []).map(i => fmtNum(i.quantity) + ' ' + esc(i.unit) + ' ' + esc(i.name)).join('<br>') || '—';
}

function pageFooter() {
  return '<p class="page-foot">Restricted barangay records · Handle household information with care · Fictional records only</p>';
}

function disasterIsActive(d) { return d && d.status !== 'CLOSED'; }
