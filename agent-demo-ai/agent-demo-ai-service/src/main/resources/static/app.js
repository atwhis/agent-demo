(() => {
  const elements = {
    form: document.querySelector('#chatForm'), input: document.querySelector('#promptInput'),
    messages: document.querySelector('#messages'), models: document.querySelector('#modelSelect'),
    importButton: document.querySelector('#importButton'), cancelImport: document.querySelector('#cancelImportButton'),
    importFields: document.querySelector('#importFields'), title: document.querySelector('#documentTitle'),
    type: document.querySelector('#documentType'), file: document.querySelector('#documentFile'),
    fileSize: document.querySelector('#documentFileSize'), removeFile: document.querySelector('#removeDocumentFile'),
    send: document.querySelector('#sendButton'), stop: document.querySelector('#stopButton'), status: document.querySelector('#statusText')
  };
  let controller = null;
  let conversationId = null;
  let generation = 0;
  let importing = false;
  let questionDraft = '';
  let selectedFile = null;
  let suggestedFileTitle = '';
  let fileNotice = '';
  const maxImportBytes = 2 * 1024 * 1024;
  const chatPlaceholder = elements.input.placeholder;
  const welcomeMessage = elements.messages.innerHTML;

  marked.setOptions({ gfm: true, breaks: true });

  async function loadModels() {
    const response = await fetch('/api/models');
    if (!response.ok) throw new Error('模型列表加载失败');
    const models = await response.json();
    elements.models.replaceChildren(...models.map(model => {
      const option = document.createElement('option');
      option.value = model.id; option.textContent = model.displayName; option.selected = model.defaultModel;
      return option;
    }));
  }

  function addMessage(role, text = '') {
    const article = document.createElement('article'); article.className = `message ${role}`;
    const avatar = document.createElement('div'); avatar.className = 'avatar'; avatar.textContent = role === 'user' ? 'YOU' : 'AI';
    const content = document.createElement('div'); content.className = 'content';
    if (role === 'user') content.textContent = text;
    article.append(avatar, content); elements.messages.append(article); scrollBottom();
    return content;
  }

  function safeMarkdown(content, text) {
    content.innerHTML = DOMPurify.sanitize(marked.parse(text), { USE_PROFILES: { html: true } });
  }

  function createSseParser(onEvent) {
    let buffer = '', trailingCr = '';
    return {
      push(chunk) {
        chunk = trailingCr + chunk;
        trailingCr = chunk.endsWith('\r') ? '\r' : '';
        if (trailingCr) {
          chunk = chunk.slice(0, -1);
        }
        buffer += chunk.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
        let boundary;
        while ((boundary = buffer.indexOf('\n\n')) >= 0) {
          const block = buffer.slice(0, boundary); buffer = buffer.slice(boundary + 2);
          let event = 'message'; const data = [];
          for (const line of block.split('\n')) {
            if (line.startsWith('event:')) event = line.slice(6).trim();
            if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
          }
          if (data.length) onEvent(event, JSON.parse(data.join('\n')));
        }
      },
      finish() {
        if (trailingCr) {
          this.push('\n');
        }
        return buffer.trim().length === 0;
      }
    };
  }

  function chartOption(spec) {
    if (spec.version !== '1.0' || !['bar', 'pie', 'line'].includes(spec.type)) throw new Error('不支持的图表协议');
    if (spec.type === 'pie') {
      const name = spec.encoding.name, value = spec.encoding.value;
      return { tooltip:{trigger:'item'}, legend:{bottom:0}, series:[{type:'pie',radius:['35%','68%'],data:spec.rows.map(row=>({name:row[name],value:row[value]}))}] };
    }
    const category = spec.encoding.category || spec.encoding.x;
    const value = spec.encoding.value || spec.encoding.y;
    const horizontal = spec.type === 'bar' && spec.options.horizontal === true;
    const categoryAxis = {type:'category',data:spec.rows.map(row=>row[category]),axisLabel:{interval:0,rotate:horizontal?0:25}};
    const valueAxis = {type:'value'};
    return { tooltip:{trigger:'axis'}, grid:{left:horizontal?110:55,right:28,bottom:horizontal?40:80,top:28,containLabel:true},
      xAxis:horizontal?valueAxis:categoryAxis, yAxis:horizontal?categoryAxis:valueAxis,
      series:[{type:spec.type,data:spec.rows.map(row=>row[value]),smooth:spec.options.smooth === true,itemStyle:{color:'#176b52'},areaStyle:spec.type==='line'?{opacity:.08}:undefined}] };
  }

  function renderChart(content, spec) {
    const node = document.createElement('div'); node.className = 'chart'; content.append(node);
    const instance = echarts.init(node); instance.setOption({...chartOption(spec), title:{text:spec.title,left:'center',textStyle:{fontSize:15}}});
    const resize = () => instance.resize(); window.addEventListener('resize', resize, {passive:true});
  }

  function renderSources(content, sources) {
    const list = document.createElement('div');
    list.className = 'sources';
    for (const source of sources) {
      const entry = document.createElement('details');
      const label = document.createElement('summary');
      const excerpt = document.createElement('p');
      label.textContent = `[${source.number}] ${source.title}${source.sectionPath ? ' · ' + source.sectionPath : ''}`;
      excerpt.textContent = source.excerpt;
      entry.append(label, excerpt);
      list.append(entry);
    }
    content.append(list);
  }

  async function submit(message) {
    const requestGeneration = ++generation;
    const requestController = new AbortController();
    addMessage('user', message);
    const content = addMessage('assistant');
    let fullText = '', pendingChart = null, pendingSources = null, completed = false, renderTimer = null;
    controller = requestController;
    setBusy(true);
    const scheduleRender = () => {
      if (requestGeneration !== generation || renderTimer) {
        return;
      }
      renderTimer = setTimeout(() => {
        renderTimer = null;
        if (requestGeneration !== generation) {
          return;
        }
        safeMarkdown(content, fullText);
        scrollBottom();
      }, 40);
    };
    try {
      const response = await fetch('/api/chat/stream', {method:'POST',headers:{'Content-Type':'application/json','Accept':'text/event-stream'},
        body:JSON.stringify({conversationId,modelId:elements.models.value,message}),signal:requestController.signal});
      if (requestGeneration !== generation) {
        return;
      }
      if (!response.ok || !response.body) {
        throw new Error(`请求失败 (${response.status})`);
      }
      const parser = createSseParser((event, data) => {
        if (requestGeneration !== generation) {
          return;
        }
        if (event === 'start') {
          conversationId = data.conversationId;
        } else if (event === 'delta') {
          fullText += data.text || '';
          scheduleRender();
        } else if (event === 'chart') {
          pendingChart = data;
        } else if (event === 'sources') {
          pendingSources = data;
        } else if (event === 'done') {
          completed = true;
          clearTimeout(renderTimer);
          renderTimer = null;
          conversationId = data.conversationId;
          safeMarkdown(content, fullText);
          content.querySelectorAll('pre code').forEach(node => hljs.highlightElement(node));
          if (pendingChart) {
            renderChart(content, pendingChart);
          }
          if (data.route === 'RAG' && pendingSources?.length) {
            renderSources(content, pendingSources);
          }
        } else if (event === 'error') {
          throw new Error(data.message || '对话失败');
        }
      });
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      while (true) {
        const {value, done} = await reader.read();
        if (done) {
          break;
        }
        parser.push(decoder.decode(value, {stream:true}));
      }
      parser.push(decoder.decode());
      parser.finish();
      if (!completed) {
        throw new Error('连接提前结束，未收到完成事件');
      }
    } catch (error) {
      if (requestGeneration !== generation) {
        return;
      }
      pendingChart = null;
      pendingSources = null;
      content.classList.add('error');
      content.textContent = error.name === 'AbortError' ? '已停止生成。' : error.message || '对话失败，请稍后重试。';
    } finally {
      if (renderTimer) {
        clearTimeout(renderTimer);
      }
      if (requestGeneration === generation) {
        controller = null;
        setBusy(false);
        scrollBottom();
      }
    }
  }

  function setBusy(busy) {
    elements.send.disabled = busy;
    elements.stop.disabled = !busy;
    elements.models.disabled = busy;
    elements.importButton.disabled = busy;
    elements.cancelImport.disabled = busy;
    elements.file.disabled = busy || !importing;
    elements.removeFile.disabled = busy;
    elements.title.disabled = busy || !importing;
    elements.type.disabled = busy || !importing;
    elements.input.readOnly = busy && importing;
    elements.status.textContent = busy ? (importing ? '正在导入，长文本建立索引可能需要几分钟…' : '正在生成…') : '就绪';
  }

  function setImportFile(file) {
    selectedFile = file;
    elements.input.hidden = Boolean(file);
    elements.removeFile.hidden = !file;
    elements.fileSize.hidden = !file;
    elements.fileSize.textContent = file ? `文件大小 ${(file.size / 1024).toFixed(1)} KiB` : '';
    const title = elements.title.value.trim();
    if (!title || title === suggestedFileTitle) {
      suggestedFileTitle = file ? Array.from(file.name.slice(0, -4)).slice(0, 80).join('') : '';
      elements.title.value = suggestedFileTitle;
    } else {
      suggestedFileTitle = '';
    }
    if (!file) {
      elements.file.value = '';
    }
  }

  function setImportMode(active) {
    importing = active;
    fileNotice = '';
    setImportFile(null);
    elements.importFields.hidden = !active;
    elements.importButton.hidden = active;
    elements.cancelImport.hidden = !active;
    elements.title.disabled = !active;
    elements.type.disabled = !active;
    elements.file.disabled = !active;
    elements.input.rows = active ? 6 : 2;
    elements.input.required = !active;
    elements.input.placeholder = active ? '粘贴纯文本正文，或选择 UTF-8 TXT 文件' : chatPlaceholder;
    elements.send.textContent = active ? '确认导入' : '发送';
    if (active) {
      questionDraft = elements.input.value;
      elements.input.value = '';
      elements.input.removeAttribute('maxlength');
    } else {
      elements.input.value = questionDraft;
      elements.input.maxLength = 4000;
      elements.title.value = '';
      elements.type.value = 'AUTO';
      questionDraft = '';
    }
  }

  async function importDocument() {
    if (fileNotice) {
      elements.status.textContent = fileNotice;
      return;
    }
    if (!selectedFile && !elements.input.value.trim()) {
      elements.status.textContent = '请粘贴正文，或选择一个 UTF-8 TXT 文件。';
      return;
    }
    if (!selectedFile && new TextEncoder().encode(elements.input.value).length > maxImportBytes) {
      elements.status.textContent = '正文超过 2 MiB，请拆成多份分别导入。';
      return;
    }
    if (!elements.title.value.trim()) {
      elements.status.textContent = '请填写文档标题。';
      return;
    }
    const requestGeneration = ++generation;
    const requestController = new AbortController();
    controller = requestController;
    setBusy(true);
    let status;
    try {
      const fields = {title:elements.title.value, documentType:elements.type.value, modelId:elements.models.value};
      const options = {method:'POST', signal:requestController.signal};
      let endpoint = '/api/knowledge/documents/text';
      if (selectedFile) {
        endpoint = '/api/knowledge/documents/upload';
        options.body = new FormData();
        options.body.append('file', selectedFile);
        for (const [key, value] of Object.entries(fields)) {
          options.body.append(key, value);
        }
      } else {
        options.headers = {'Content-Type':'application/json'};
        options.body = JSON.stringify({...fields, content:elements.input.value});
      }
      const response = await fetch(endpoint, options);
      const data = await response.json();
      if (requestGeneration !== generation) {
        return;
      }
      if (!response.ok) {
        throw new Error(data.message || '导入失败，请检查输入后重试。');
      }
      setImportMode(false);
      status = `${data.duplicate ? '已存在' : '已导入'}《${data.title}》 · ${data.documentType} · ${data.chunkCount} 个片段 · 可以提问`;
    } catch (error) {
      status = error.name === 'AbortError' ? '已停止等待。资料可能已入库，重试会按正文去重。' : error.message;
    } finally {
      if (requestGeneration === generation) {
        controller = null;
        setBusy(false);
        elements.status.textContent = status || '导入失败，请重试。';
      }
    }
  }

  function scrollBottom() { requestAnimationFrame(() => window.scrollTo({top:document.body.scrollHeight,behavior:'smooth'})); }
  elements.form.addEventListener('submit', event => {
    event.preventDefault();
    if (controller) {
      return;
    }
    if (importing) {
      importDocument();
    } else if (elements.input.value.trim()) {
      const message = elements.input.value.trim();
      elements.input.value = '';
      submit(message);
    }
  });
  elements.input.addEventListener('keydown', event => {
    if (!importing && event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      elements.form.requestSubmit();
    }
  });
  elements.importButton.addEventListener('click', () => setImportMode(true));
  elements.cancelImport.addEventListener('click', () => {
    setImportMode(false);
    setBusy(false);
  });
  elements.input.addEventListener('input', () => {
    if (importing && fileNotice) {
      fileNotice = '';
      elements.status.textContent = '就绪';
    }
  });
  elements.removeFile.addEventListener('click', () => {
    setImportFile(null);
    fileNotice = '';
    elements.status.textContent = '就绪';
  });
  elements.file.addEventListener('change', () => {
    const file = elements.file.files[0];
    fileNotice = '';
    if (!file) {
      setImportFile(null);
    } else if (!/\.txt$/i.test(file.name) || file.size === 0) {
      fileNotice = '请选择一个非空的 UTF-8 TXT 文件。';
      setImportFile(null);
    } else if (file.size > maxImportBytes) {
      fileNotice = `TXT 文件为 ${(file.size / 1024).toFixed(1)} KiB，超过 2 MiB，请拆成多份分别导入。`;
      setImportFile(null);
    } else {
      setImportFile(file);
    }
    elements.status.textContent = fileNotice || (selectedFile ? '已选择 TXT，点击确认导入。' : '就绪');
  });
  elements.stop.addEventListener('click', () => controller?.abort());
  document.querySelector('#newConversationButton').addEventListener('click', async () => {
    const oldId = conversationId;
    const resetGeneration = ++generation;
    controller?.abort(); controller = null; conversationId = null;
    if (importing) {
      setImportMode(false);
    }
    elements.input.value = ''; elements.messages.innerHTML = welcomeMessage; setBusy(false);
    try {
      if (oldId) {
        const response = await fetch('/api/conversations/' + encodeURIComponent(oldId), {method:'DELETE'});
        if (!response.ok) {
          throw new Error('清理失败');
        }
      }
    } catch {
      if (resetGeneration === generation) {
        elements.status.textContent = '新会话已开启，但旧会话未能清理。';
      }
    }
  });
  elements.messages.addEventListener('click', event => {
    const button = event.target.closest('.suggestions button');
    if (button && !importing && !controller) {
      elements.input.value = button.textContent;
      elements.form.requestSubmit();
    }
  });
  loadModels().catch(error => { elements.models.innerHTML='<option value="">模型不可用</option>'; elements.status.textContent=error.message; elements.send.disabled=true; });
})();
