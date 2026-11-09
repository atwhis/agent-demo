// Run with node; no browser, model keys or npm packages required.
const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const script = fs.readFileSync(path.join(__dirname, '../../main/resources/static/app.js'), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));

class Element {
  constructor() { this.listeners = {}; this.children = []; this.value = ''; this.innerHTML = 'welcome'; this.classList = {add() {}}; }
  set innerHTML(value) { this.html = value; this.children = []; }
  get innerHTML() { return this.html; }
  addEventListener(name, fn) { this.listeners[name] = fn; }
  append(...children) { this.children.push(...children); }
  replaceChildren(...children) { this.children = children; }
  querySelectorAll() { return []; }
  removeAttribute(name) { delete this[name]; }
}
function fixture() {
  const elements = Object.fromEntries(['chatForm', 'promptInput', 'messages', 'modelSelect', 'sendButton', 'stopButton', 'statusText', 'newConversationButton', 'importButton', 'cancelImportButton', 'importFields', 'documentTitle', 'documentType', 'documentFile', 'documentFileSize', 'removeDocumentFile']
    .map(id => [id, new Element()]));
  const requests = [], timers = [];
  let deleteOk = true;
  let importOk = false;
  const fetch = async (url, options) => {
    if (url === '/api/models') {
      return {ok:true, json:async () => [{id:'m', displayName:'M', defaultModel:true}]};
    }
    if (url === '/api/knowledge/documents/text' || url === '/api/knowledge/documents/upload') {
      requests.push({url, options});
      return {ok:importOk, json:async () => importOk
        ? {title:'规则', documentType:'制度规则', chunkCount:3, duplicate:false}
        : {message:'导入失败'}};
    }
    if (options.method === 'DELETE') {
      requests.push({url, options});
      return {ok:deleteOk};
    }
    let resolve;
    const buffered = [];
    const reader = {read:() => buffered.length ? Promise.resolve(buffered.shift()) : new Promise(r => { resolve = r; })};
    const push = item => {
      if (resolve) {
        const r = resolve; resolve = null; r(item);
      } else {
        buffered.push(item);
      }
    };
    const request = {url, options, end:() => push({done:true}), event:(event, data) => {
      push({done:false, value:new TextEncoder().encode(`event: ${event}\r\ndata: ${JSON.stringify(data)}\r\n\r\n`)});
    }};
    requests.push(request);
    return {ok:true, body:{getReader:() => reader}};
  };
  vm.runInNewContext(script, {
    document:{querySelector:selector => elements[selector.slice(1)], createElement:() => new Element(), body:{scrollHeight:0}},
    window:{scrollTo() {}, addEventListener() {}}, requestAnimationFrame() {},
    fetch, AbortController, FormData, TextDecoder, TextEncoder, marked:{setOptions() {}, parse:text => text}, DOMPurify:{sanitize:text => text},
    hljs:{highlightElement() {}}, echarts:{init() { throw Error('Unexpected stale chart'); }},
    setTimeout:fn => { timers.push(fn); return timers.length; }, clearTimeout() {}
  });
  return {elements, requests, timers, importSuccess:() => { importOk = true; }, deleteFailure:() => { deleteOk = false; }, send:async text => {
    elements.promptInput.value = text;
    elements.chatForm.listeners.submit({preventDefault() {}});
    await flush();
    return requests.at(-1);
  }};
}

test('new conversation ignores late delta/chart/done, render timer and old finally', async () => {
  const f = fixture();
  await flush();
  const old = await f.send('old');
  old.event('start', {conversationId:'old-id', turnId:'old-turn'});
  old.event('delta', {text:'partial'});
  await flush();
  const oldContent = f.elements.messages.children.at(-1).children[1];
  await f.elements.newConversationButton.listeners.click();
  assert.equal(old.options.signal.aborted, true);
  assert.equal(f.requests.at(-1).url, '/api/conversations/old-id');
  assert.equal(f.elements.promptInput.value, '');
  const current = await f.send('new');
  assert.equal(JSON.parse(current.options.body).conversationId, null);
  old.event('delta', {text:'late'});
  old.event('chart', {version:'1.0', type:'bar'});
  old.event('sources', [{title:'late source'}]);
  old.event('done', {conversationId:'old-id', turnId:'old-turn'});
  old.end();
  f.timers.forEach(fn => fn());
  await flush();
  assert.equal(oldContent.innerHTML, 'welcome');
  assert.equal(f.elements.sendButton.disabled, true);
  assert.equal(f.elements.stopButton.disabled, false);
  current.event('start', {conversationId:'new-id', turnId:'new-turn'});
  current.event('done', {conversationId:'new-id', turnId:'new-turn'});
  current.end();
  await flush();
  assert.equal(f.elements.sendButton.disabled, false);
  const next = await f.send('next');
  assert.equal(JSON.parse(next.options.body).conversationId, 'new-id');
  next.end();
  await flush();
});

test('start retains first-turn identity after an error and clear failure is visible', async () => {
  const f = fixture();
  await flush();
  const request = await f.send('first');
  request.event('start', {conversationId:'failed-id', turnId:'failed-turn'});
  request.event('error', {message:'友好错误'});
  await flush();
  assert.equal(f.elements.messages.children.at(-1).children[1].textContent, '友好错误');
  f.deleteFailure();
  await f.elements.newConversationButton.listeners.click();
  assert.equal(f.requests.at(-1).url, '/api/conversations/failed-id');
  assert.match(f.elements.statusText.textContent, /旧会话未能清理/);
});

test('import keeps the draft, preserves failed text and restores chat on success', async () => {
  const f = fixture();
  await flush();
  f.elements.promptInput.value = '未发送的问题';
  f.elements.importButton.listeners.click();
  assert.equal(f.elements.importFields.hidden, false);
  f.elements.cancelImportButton.listeners.click();
  assert.equal(f.elements.promptInput.value, '未发送的问题');
  assert.equal(f.requests.length, 0);
  f.elements.importButton.listeners.click();
  f.elements.documentTitle.value = '规则';
  f.elements.documentType.value = 'AUTO';
  const request = await f.send('导入正文'.repeat(2000));
  assert.equal(request.url, '/api/knowledge/documents/text');
  assert.equal(f.elements.importFields.hidden, false);
  assert.match(f.elements.statusText.textContent, /导入失败/);
  assert.equal(f.elements.promptInput.value, '导入正文'.repeat(2000));
  f.importSuccess();
  await f.send(f.elements.promptInput.value);
  assert.equal(f.elements.importFields.hidden, true);
  assert.equal(f.elements.promptInput.value, '未发送的问题');
  assert.match(f.elements.statusText.textContent, /制度规则 · 3 个片段/);
});

test('only successful RAG done renders sources and preserves raw text safely', async () => {
  const f = fixture();
  await flush();
  const source = {number:1, title:'<img src=x onerror=alert(1)>', sectionPath:'<b>节</b>', excerpt:'<script>alert(1)</script>'};
  const failing = await f.send('失败');
  failing.event('start', {conversationId:'id', turnId:'failed'});
  failing.event('sources', [source]);
  failing.event('error', {message:'失败'});
  await flush();
  assert.equal(f.elements.messages.children.at(-1).children[1].children.length, 0);
  const success = await f.send('成功');
  success.event('start', {conversationId:'id', turnId:'successful'});
  success.event('sources', [source]);
  await flush();
  const content = f.elements.messages.children.at(-1).children[1];
  assert.equal(content.children.length, 0);
  success.event('done', {conversationId:'id', turnId:'successful', route:'RAG'});
  success.end();
  await flush();
  const [label, excerpt] = content.children[0].children[0].children;
  assert.equal(label.textContent, '[1] ' + source.title + ' · ' + source.sectionPath);
  assert.equal(excerpt.textContent, source.excerpt);
});

test('file upload preserves the paste draft, rejects oversized files and submits a large TXT directly', async () => {
  const f = fixture();
  const {promptInput, documentFile, documentTitle, documentType, modelSelect, statusText, importButton, removeDocumentFile} = f.elements;
  await flush();
  importButton.listeners.click();
  assert.equal(promptInput.required, false);
  documentFile.value = '西游记.txt';
  documentFile.files = [{name: '西游记.txt', size: 2 * 1024 * 1024 + 1024}];
  await documentFile.listeners.change();
  assert.equal(documentFile.value, '');
  const sizeError = statusText.textContent;
  assert.match(sizeError, /2049\.0 KiB.*超过 2 MiB.*拆成多份/);
  await f.send('');
  assert.equal(statusText.textContent, sizeError);
  assert.equal(f.requests.length, 0);

  const file = new File([new Uint8Array(1806411)], '三国演义.txt');
  file.arrayBuffer = () => { throw Error('The browser must not read the selected file into the composer'); };
  promptInput.value = '未提交的粘贴正文';
  documentFile.files = [file];
  await documentFile.listeners.change();
  assert.equal(documentTitle.value, '三国演义');
  assert.equal(promptInput.hidden, true);
  assert.equal(promptInput.value, '未提交的粘贴正文');
  removeDocumentFile.listeners.click();
  assert.equal(promptInput.hidden, false);
  assert.equal(promptInput.value, '未提交的粘贴正文');
  assert.equal(documentTitle.value, '');

  documentType.value = '其他';
  modelSelect.value = 'm';
  documentFile.files = [file];
  await documentFile.listeners.change();
  const failed = await f.send(promptInput.value);
  assert.equal(failed.url, '/api/knowledge/documents/upload');
  assert.equal(promptInput.hidden, true);
  assert.equal(documentTitle.value, '三国演义');
  f.importSuccess();
  const request = await f.send(promptInput.value);
  assert.equal(request.url, '/api/knowledge/documents/upload');
  assert.deepEqual([...request.options.body.keys()], ['file', 'title', 'documentType', 'modelId']);
  assert.equal(request.options.body.get('file'), file);
  assert.equal(request.options.body.get('title'), '三国演义');
  assert.equal(request.options.body.get('documentType'), '其他');
  assert.equal(request.options.body.get('modelId'), 'm');
  assert.equal(promptInput.hidden, false);
  assert.equal(promptInput.required, true);
});
