(() => {
  const elements = {
    form: document.querySelector('#chatForm'), input: document.querySelector('#promptInput'),
    messages: document.querySelector('#messages'), models: document.querySelector('#modelSelect'),
    send: document.querySelector('#sendButton'), stop: document.querySelector('#stopButton'), status: document.querySelector('#statusText')
  };
  let controller = null;
  let conversationId = null;

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
    let buffer = '';
    return {
      push(chunk) {
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
      finish() { return buffer.trim().length === 0; }
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

  async function submit(message) {
    addMessage('user', message);
    const content = addMessage('assistant'); let fullText = ''; let pendingChart = null; let completed = false; let renderTimer = null;
    controller = new AbortController(); setBusy(true);
    const scheduleRender = () => { if (renderTimer) return; renderTimer = setTimeout(() => { renderTimer = null; safeMarkdown(content, fullText); scrollBottom(); }, 40); };
    try {
      const response = await fetch('/api/chat/stream', {method:'POST',headers:{'Content-Type':'application/json','Accept':'text/event-stream'},
        body:JSON.stringify({conversationId,modelId:elements.models.value,message}),signal:controller.signal});
      if (!response.ok || !response.body) throw new Error(`请求失败 (${response.status})`);
      const parser = createSseParser((event, data) => {
        if (event === 'delta') { fullText += data.text || ''; scheduleRender(); }
        else if (event === 'chart') pendingChart = data;
        else if (event === 'done') { completed = true; conversationId = data.conversationId; safeMarkdown(content, fullText); content.querySelectorAll('pre code').forEach(node=>hljs.highlightElement(node)); if (pendingChart) renderChart(content,pendingChart); }
        else if (event === 'error') throw new Error(data.message || '对话失败');
      });
      const reader = response.body.getReader(); const decoder = new TextDecoder();
      while (true) { const {value,done} = await reader.read(); if (done) break; parser.push(decoder.decode(value,{stream:true})); }
      parser.push(decoder.decode()); parser.finish();
      if (!completed) throw new Error('连接提前结束，未收到完成事件');
    } catch (error) {
      pendingChart = null;
      if (error.name === 'AbortError') { content.classList.add('error'); content.textContent = '已停止生成。'; }
      else { content.classList.add('error'); content.textContent = error.message || '对话失败，请稍后重试。'; }
    } finally { if (renderTimer) clearTimeout(renderTimer); controller = null; setBusy(false); scrollBottom(); }
  }

  function setBusy(busy) { elements.send.disabled = busy; elements.stop.disabled = !busy; elements.models.disabled = busy; elements.status.textContent = busy ? '正在生成…' : '就绪'; }
  function scrollBottom() { requestAnimationFrame(() => window.scrollTo({top:document.body.scrollHeight,behavior:'smooth'})); }
  elements.form.addEventListener('submit', event => { event.preventDefault(); const message=elements.input.value.trim(); if (!message || controller) return; elements.input.value=''; submit(message); });
  elements.input.addEventListener('keydown', event => { if (event.key==='Enter' && !event.shiftKey) { event.preventDefault(); elements.form.requestSubmit(); } });
  elements.stop.addEventListener('click', () => controller?.abort());
  document.querySelectorAll('.suggestions button').forEach(button => button.addEventListener('click', () => { elements.input.value=button.textContent; elements.form.requestSubmit(); }));
  loadModels().catch(error => { elements.models.innerHTML='<option value="">模型不可用</option>'; elements.status.textContent=error.message; elements.send.disabled=true; });
})();
