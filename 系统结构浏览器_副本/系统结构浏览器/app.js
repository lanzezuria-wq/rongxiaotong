/* 融销通 · 系统结构浏览器 */
(function () {
  const D = window.RXT_DATA;
  const $ = (id) => document.getElementById(id);

  /* ---------- 工具 ---------- */
  const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

  /* 负责人徽章：P1 地基/用户 · P2 交易 · P3 融资专家 · P4 前端 · 我 · 共(混合) */
  const OCLS = { P1: "o-p1", P2: "o-p2", P3: "o-p3", P4: "o-p4", "我": "o-me", "共": "o-mix" };
  const ownerBadge = (o, big) =>
    '<span class="owner ' + (OCLS[o] || "o-mix") + (big ? " owner-big" : "") + '">' + esc(o || "共") + "</span>";
  const ownerNote = (o) => (D.ownerNote && D.ownerNote[o]) ? D.ownerNote[o] : "";

  const KEYWORDS = {
    java: ["public","private","protected","class","interface","enum","extends","implements","new","return","if","else","for","while","do","break","continue","switch","case","default","try","catch","finally","throw","throws","static","final","void","abstract","import","package","this","super","null","true","false","int","long","double","float","boolean","char","byte","short","synchronized","volatile","transient","instanceof","record","var","yield","sealed","permits"],
    js: ["const","let","var","function","return","if","else","for","while","class","import","export","from","default","new","async","await","try","catch","throw","this","null","true","false","typeof","instanceof","extends","super","switch","case","break","continue","delete","in","of"],
    vue: ["const","let","var","function","return","if","else","for","while","import","export","from","default","new","async","await","try","catch","throw","this","null","true","false","typeof","extends","setup","ref","computed","watch","props","emit","defineProps","defineEmits","onMounted"],
    sql: ["SELECT","FROM","WHERE","INSERT","INTO","VALUES","UPDATE","DELETE","CREATE","TABLE","DROP","ALTER","ADD","PRIMARY","KEY","FOREIGN","UNIQUE","INDEX","NOT","NULL","DEFAULT","AUTO_INCREMENT","COMMENT","ENGINE","CHARSET","COLLATE","INT","BIGINT","VARCHAR","DECIMAL","TINYINT","DATETIME","TEXT","JSON","AND","OR","IN","LIKE","ORDER","BY","GROUP","LIMIT","OFFSET","JOIN","LEFT","RIGHT","INNER","ON","AS","SET","USE","IF","EXISTS","PROCEDURE","BEGIN","END","CALL","DELIMITER","UNSIGNED"],
    python: ["def","class","return","if","elif","else","for","while","import","from","as","try","except","finally","with","lambda","None","True","False","self","print","assert","raise","yield","pass","break","continue","global","not","and","or","in","is"],
    yaml: ["true","false","null","yes","no"],
    xml: [], json: [], css: [], md: [], ini: [], text: []
  };

  function highlight(code, lang) {
    if (lang === "md" || lang === "text") return esc(code);
    const kw = KEYWORDS[lang] || [];
    const alts = [];
    if (lang === "sql") alts.push("--[^\\n]*");
    if (lang === "python" || lang === "yaml" || lang === "ini") alts.push("#[^\\n]*");
    if (lang === "xml" || lang === "vue") alts.push("<!--[\\s\\S]*?-->");
    alts.push("/\\*[\\s\\S]*?\\*/");
    alts.push("//[^\\n]*");
    alts.push('"(?:[^"\\\\]|\\\\.)*"');
    alts.push("'(?:[^'\\\\]|\\\\.)*'");
    if (lang === "java") alts.push("@[A-Za-z]\\w*");
    if (lang === "xml" || lang === "vue") alts.push("</?[A-Za-z][\\w:.-]*");
    if (kw.length) alts.push("\\b(?:" + kw.join("|") + ")\\b");
    alts.push("\\b\\d+(?:\\.\\d+)?\\b");

    const re = new RegExp("(" + alts.join("|") + ")", "g");
    let out = "", last = 0, m;
    while ((m = re.exec(code)) !== null) {
      out += esc(code.slice(last, m.index));
      const t = m[0];
      let cls = "kw";
      if (/^(--|#|\/\/|\/\*|<!--)/.test(t)) cls = "com";
      else if (/^['"]/.test(t)) cls = "str";
      else if (/^@/.test(t)) cls = "ann";
      else if (/^<\/?/.test(t)) cls = "tag";
      else if (/^\d/.test(t)) cls = "num";
      out += '<span class="' + cls + '">' + esc(t) + "</span>";
      last = m.index + t.length;
    }
    return out + esc(code.slice(last));
  }

  /* ---------- 极简 Markdown 渲染 ---------- */
  function renderMarkdown(src) {
    const inline = (s) => {
      s = esc(s);
      s = s.replace(/`([^`]+)`/g, "<code>$1</code>");
      s = s.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
      s = s.replace(/(^|[^*])\*([^*]+)\*/g, "$1<em>$2</em>");
      s = s.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>');
      return s;
    };
    const lines = src.split("\n");
    const out = [];
    let i = 0;
    while (i < lines.length) {
      const line = lines[i];
      if (/^\s*```/.test(line)) {                       // 代码块
        const buf = [];
        i++;
        while (i < lines.length && !/^\s*```/.test(lines[i])) { buf.push(lines[i]); i++; }
        i++;
        out.push("<pre><code>" + esc(buf.join("\n")) + "</code></pre>");
        continue;
      }
      if (/^\s*\|/.test(line) && i + 1 < lines.length && /^\s*\|[\s:|-]+\|/.test(lines[i + 1])) { // 表格
        const cells = (l) => l.trim().replace(/^\|/, "").replace(/\|$/, "").split("|").map((c) => c.trim());
        const head = cells(line);
        i += 2;
        const body = [];
        while (i < lines.length && /^\s*\|/.test(lines[i])) { body.push(cells(lines[i])); i++; }
        let h = "<table><thead><tr>" + head.map((c) => "<th>" + inline(c) + "</th>").join("") + "</tr></thead><tbody>";
        body.forEach((r) => { h += "<tr>" + r.map((c) => "<td>" + inline(c) + "</td>").join("") + "</tr>"; });
        out.push(h + "</tbody></table>");
        continue;
      }
      let m;
      if ((m = line.match(/^(#{1,6})\s+(.*)$/))) {      // 标题
        const lv = m[1].length;
        out.push("<h" + lv + ">" + inline(m[2]) + "</h" + lv + ">");
        i++; continue;
      }
      if (/^\s*(---|\*\*\*|___)\s*$/.test(line)) { out.push("<hr>"); i++; continue; }
      if (/^\s*>/.test(line)) {                          // 引用
        const buf = [];
        while (i < lines.length && /^\s*>/.test(lines[i])) { buf.push(lines[i].replace(/^\s*>\s?/, "")); i++; }
        out.push("<blockquote>" + inline(buf.join("<br>")) + "</blockquote>");
        continue;
      }
      if (/^\s*[-*+]\s+/.test(line)) {                   // 无序列表
        const buf = [];
        while (i < lines.length && /^\s*[-*+]\s+/.test(lines[i])) { buf.push(lines[i].replace(/^\s*[-*+]\s+/, "")); i++; }
        out.push("<ul>" + buf.map((x) => "<li>" + inline(x) + "</li>").join("") + "</ul>");
        continue;
      }
      if (/^\s*\d+\.\s+/.test(line)) {                   // 有序列表
        const buf = [];
        while (i < lines.length && /^\s*\d+\.\s+/.test(lines[i])) { buf.push(lines[i].replace(/^\s*\d+\.\s+/, "")); i++; }
        out.push("<ol>" + buf.map((x) => "<li>" + inline(x) + "</li>").join("") + "</ol>");
        continue;
      }
      if (line.trim() === "") { i++; continue; }
      const buf = [];                                    // 段落
      while (i < lines.length && lines[i].trim() !== "" &&
             !/^(#{1,6}\s|```|\s*>|\s*[-*+]\s|\s*\d+\.\s|\s*\|)/.test(lines[i])) { buf.push(lines[i]); i++; }
      if (buf.length) out.push("<p>" + inline(buf.join("\n")).replace(/\n/g, "<br>") + "</p>");
      else i++;
    }
    return out.join("\n");
  }

  /* ---------- 状态 ---------- */
  const state = {
    sel: null,
    open: new Set(["", "demo1", "demo1/src", "demo1/src/main", "demo1/src/main/java",
                   "demo1/src/main/java/com/rxt"]),
    q: "",
    owner: "",           // 负责人筛选：空 = 全部
    closed: new Set(),   // 筛选态下被手动折叠的目录
    mdRaw: false
  };

  /* ---------- 目录树 ---------- */
  function iconFor(name, type) {
    if (type === "dir") return "📁";
    const ext = name.slice(name.lastIndexOf("."));
    const map = { ".java": "☕", ".vue": "🟩", ".js": "🟨", ".sql": "🗄️", ".md": "📘",
                  ".yml": "⚙️", ".xml": "🧩", ".json": "⚙️", ".py": "🐍", ".css": "🎨" };
    return map[ext] || "📄";
  }
  function extClass(name) {
    const ext = name.slice(name.lastIndexOf(".")).replace(".", "");
    return "ic-" + (ext || "txt");
  }
  function markName(name, q) {
    if (!q) return esc(name);
    const idx = name.toLowerCase().indexOf(q.toLowerCase());
    if (idx < 0) return esc(name);
    return esc(name.slice(0, idx)) + "<mark>" + esc(name.slice(idx, idx + q.length)) +
           "</mark>" + esc(name.slice(idx + q.length));
  }

  function visibleSet(q, owner) {
    if (!q && !owner) return null;
    const s = new Set([""]);
    const ql = (q || "").toLowerCase();
    Object.keys(D.files).forEach((p) => {
      const okQ = !q || p.toLowerCase().includes(ql);
      const okO = !owner || D.files[p].owner === owner;
      if (okQ && okO) {
        s.add(p);
        const parts = p.split("/");
        let cur = "";
        for (let i = 0; i < parts.length - 1; i++) { cur = cur ? cur + "/" + parts[i] : parts[i]; s.add(cur); }
      }
    });
    return s;
  }

  function nodeEl(node, vis) {
    if (vis && !vis.has(node.path)) return null;
    const li = document.createElement("li");

    const row = document.createElement("div");
    row.className = "row " + node.type + (state.sel === node.path ? " selected" : "");
    const filtering = !!(state.q || state.owner);
    // 筛选/搜索时默认把命中项全展开，但允许手动折叠（记在 state.closed 里）
    const isOpen = filtering
      ? (vis && vis.has(node.path) && !state.closed.has(node.path))
      : state.open.has(node.path);

    if (node.type === "dir") {
      const ar = document.createElement("span");
      ar.className = "arrow" + (isOpen ? " open" : "");
      ar.textContent = "▶";
      row.appendChild(ar);
      row.appendChild(Object.assign(document.createElement("span"), { className: "icon", textContent: "📁" }));
      const main = document.createElement("span");
      main.className = "main";
      const info0 = D.dirs[node.path] || {};
      const nm = document.createElement("span");
      nm.className = "nametext";
      nm.innerHTML = ownerBadge(info0.owner) + markName(node.name, state.q);
      main.appendChild(nm);
      const info = info0;
      if (info.desc) {                       // 目录是干嘛用的
        const note = document.createElement("span");
        note.className = "note";
        note.textContent = info.desc;
        note.title = info.desc;
        main.appendChild(note);
      }
      row.appendChild(main);
      if (info.count) {
        const b = document.createElement("span");
        b.className = "badge";
        b.textContent = info.count;
        row.appendChild(b);
      }
      row.onclick = (e) => {
        e.stopPropagation();
        if (state.q || state.owner) {          // 筛选态：折叠/展开记在 closed 里
          if (state.closed.has(node.path)) state.closed.delete(node.path); else state.closed.add(node.path);
        } else {
          if (state.open.has(node.path)) state.open.delete(node.path); else state.open.add(node.path);
        }
        if (state.q) { state.q = ""; $("search").value = ""; }
        selectDir(node.path);
        renderTree();
      };
    } else {
      row.appendChild(Object.assign(document.createElement("span"), { className: "arrow", textContent: "" }));
      const ic = document.createElement("span");
      ic.className = "icon " + extClass(node.name);
      ic.textContent = iconFor(node.name, "file");
      row.appendChild(ic);
      const main = document.createElement("span");
      main.className = "main";
      const fi = D.files[node.path];
      const nm = document.createElement("span");
      nm.className = "nametext";
      nm.innerHTML = ownerBadge(fi && fi.owner) + markName(node.name, state.q);
      main.appendChild(nm);
      if (fi && fi.desc) {                   // 这个文件是干嘛用的
        const note = document.createElement("span");
        note.className = "note";
        note.textContent = fi.desc;
        note.title = fi.desc;
        main.appendChild(note);
      }
      row.appendChild(main);
      row.onclick = (e) => { e.stopPropagation(); selectFile(node.path); renderTree(); };
    }
    li.appendChild(row);

    if (node.type === "dir" && isOpen && node.children) {
      const ul = document.createElement("ul");
      node.children.forEach((c) => { const el = nodeEl(c, vis); if (el) ul.appendChild(el); });
      li.appendChild(ul);
    }
    return li;
  }

  function renderTree() {
    const el = $("tree");
    el.innerHTML = "";
    const vis = visibleSet(state.q, state.owner);
    const ul = document.createElement("ul");
    ul.appendChild(nodeEl(D.tree, vis));
    el.appendChild(ul);
  }

  /* ---------- 内容区 ---------- */
  function crumb(path, isDir) {
    const parts = path ? path.split("/") : [];
    const html = ['<a href="#" data-dir="">springbo</a>'];
    let cur = "";
    parts.forEach((p, i) => {
      cur = cur ? cur + "/" + p : p;
      const last = i === parts.length - 1;
      html.push('<span style="opacity:.5"> / </span>');
      html.push(last && !isDir ? "<b>" + esc(p) + "</b>"
                : '<a href="#" data-dir="' + esc(cur) + '">' + esc(p) + "</a>");
    });
    return '<div class="crumb">' + html.join("") + "</div>";
  }

  function selectDir(path) {
    state.sel = path;
    const info = D.dirs[path] || { desc: "目录", count: 0 };
    const node = findNode(D.tree, path);
    const kids = node && node.children ? node.children : [];
    const dirs = kids.filter((k) => k.type === "dir");
    const files = kids.filter((k) => k.type === "file");

    let h = crumb(path, true);
    h += '<div class="desc"><span class="tag">目录说明</span>' + ownerBadge(info.owner, true) +
         '<span class="text">' + esc(info.desc) + '</span><div class="meta">负责人：<b>' +
         esc(info.owner || "共") + "</b>" +
         (ownerNote(info.owner) ? "（" + esc(ownerNote(info.owner)) + "）" : "") +
         " · 共 " + (info.count || 0) +
         " 个文件 · 直接子目录 " + dirs.length + " 个 · 直接文件 " + files.length + " 个</div></div>";

    h += '<div class="card"><div class="card-head"><span class="title">这一层都有什么</span></div><div style="padding:12px"><div class="dirlist">';
    dirs.forEach((d) => {
      const di = D.dirs[d.path] || { desc: "", count: 0 };
      h += '<div class="diritem" data-dir="' + esc(d.path) + '"><span>📁</span><span class="nm">' +
           ownerBadge(di.owner) + esc(d.name) + '</span><span class="ds">' + di.count + " 个文件</span></div>";
    });
    files.forEach((f) => {
      const fi = D.files[f.path] || {};
      h += '<div class="diritem" data-file="' + esc(f.path) + '"><span>' + iconFor(f.name, "file") +
           '</span><span class="nm">' + ownerBadge(fi.owner) + esc(f.name) + '</span><span class="ds">' +
           (fi.lines ? fi.lines + " 行" : "") + "</span></div>";
    });
    h += "</div></div></div>";

    $("content").innerHTML = h;
    bindContent();
    $("content").scrollTop = 0;
  }

  function selectFile(path) {
    state.sel = path;
    state.mdRaw = false;
    const f = D.files[path];
    if (!f) return;

    let h = crumb(path, false);
    h += '<div class="desc"><span class="tag">这个文件是干什么的</span>' + ownerBadge(f.owner, true) +
         '<span class="text">' + esc(f.desc) + '</span><div class="meta">负责人：<b>' +
         esc(f.owner || "共") + "</b>" +
         (ownerNote(f.owner) ? "（" + esc(ownerNote(f.owner)) + "）" : "") +
         " · " + f.lang.toUpperCase() + " · " + f.lines +
         " 行 · " + (f.size / 1024).toFixed(1) + " KB</div></div>";

    const isMd = f.lang === "md";
    h += '<div class="card"><div class="card-head"><span class="title">源码</span><span class="sp">' +
         (isMd ? '<button id="mdToggle" class="on">文档视图</button><button id="rawToggle">原始 Markdown</button>' : "") +
         '<button id="copyBtn">复制代码</button></span></div>';

    if (isMd) {
      h += '<div id="mdView" class="md">' + renderMarkdown(f.code) + "</div>";
      h += '<div id="rawView" style="display:none"><div class="code-wrap"><div class="gutter">' +
           lineNumbers(f.lines) + '</div><pre class="code">' + esc(f.code) + "</pre></div></div>";
    } else {
      const lines = f.code.split("\n");
      const body = highlight(f.code, f.lang);
      h += '<div class="code-wrap"><div class="gutter">' + lineNumbers(f.lines) +
           '</div><pre class="code">' + body + "</pre></div>";
    }
    h += "</div>";

    $("content").innerHTML = h;
    bindContent();
    $("content").scrollTop = 0;
  }

  function lineNumbers(n) {
    let s = "";
    for (let i = 1; i <= n; i++) s += i + "\n";
    return esc(s);
  }

  function findNode(node, path) {
    if (node.path === path) return node;
    if (!node.children) return null;
    for (const c of node.children) { const r = findNode(c, path); if (r) return r; }
    return null;
  }

  function bindContent() {
    $("content").querySelectorAll("[data-dir]").forEach((el) => {
      el.onclick = (e) => {
        e.preventDefault();
        const p = el.getAttribute("data-dir");
        state.open.add(p);
        let cur = p;
        while (cur) { const i = cur.lastIndexOf("/"); cur = i < 0 ? "" : cur.slice(0, i); if (cur) state.open.add(cur); }
        selectDir(p); renderTree();
      };
    });
    $("content").querySelectorAll("[data-file]").forEach((el) => {
      el.onclick = () => { selectFile(el.getAttribute("data-file")); renderTree(); };
    });
    const cb = $("copyBtn");
    if (cb) cb.onclick = () => {
      const code = D.files[state.sel].code;
      const done = () => { cb.textContent = "已复制 ✓"; setTimeout(() => (cb.textContent = "复制代码"), 1400); };
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(code).then(done, () => fallbackCopy(code, done));
      } else fallbackCopy(code, done);
    };
    const mt = $("mdToggle"), rt = $("rawToggle");
    if (mt && rt) {
      mt.onclick = () => { $("mdView").style.display = ""; $("rawView").style.display = "none"; mt.classList.add("on"); rt.classList.remove("on"); };
      rt.onclick = () => { $("mdView").style.display = "none"; $("rawView").style.display = ""; rt.classList.add("on"); mt.classList.remove("on"); };
    }
  }

  function fallbackCopy(text, done) {
    const ta = document.createElement("textarea");
    ta.value = text;
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand("copy"); done(); } catch (e) { alert("复制失败，请手动选择复制"); }
    document.body.removeChild(ta);
  }

  /* ---------- 顶栏 ---------- */
  const total = Object.keys(D.files).length;
  const nJava = Object.keys(D.files).filter((p) => p.endsWith(".java")).length;
  const nVue = Object.keys(D.files).filter((p) => p.endsWith(".vue")).length;
  const byOwner = ["P1", "P2", "P3", "P4", "我"]
    .map((o) => o + " " + Object.values(D.files).filter((f) => f.owner === o).length)
    .join(" · ");
  $("stats").textContent = total + " 个文件 · " + Object.keys(D.dirs).length +
    " 个目录 ｜ " + byOwner + " ｜ " + nJava + " Java · " + nVue + " Vue";

  $("search").oninput = (e) => { state.q = e.target.value.trim(); renderTree(); };
  $("clearSearch").onclick = () => { state.q = ""; $("search").value = ""; renderTree(); };
  // 按负责人筛选
  const OWNER_HOME = {                       // 切到某人时右侧默认打开的主目录
    P1: "demo1/src/main/java/com/rxt",
    P2: "demo1/src/main/java/com/rxt/trade",
    P3: "demo1/src/main/java/com/rxt/finance",
    P4: "rxt-frontend/src",
    "我": "我的说明文档"
  };
  $("filters").querySelectorAll(".fbtn").forEach((b) => {
    b.onclick = () => {
      state.owner = b.getAttribute("data-owner");
      state.closed = new Set();
      $("filters").querySelectorAll(".fbtn").forEach((x) => x.classList.remove("on"));
      b.classList.add("on");
      renderTree();
      const home = OWNER_HOME[state.owner];  // 右侧跟着切到他的主目录，避免左右不一致
      if (home && D.dirs[home]) selectDir(home);
    };
  });
  $("expandAll").onclick = () => { Object.keys(D.dirs).forEach((p) => state.open.add(p)); state.q = ""; $("search").value = ""; renderTree(); };
  $("collapseAll").onclick = () => { state.open = new Set([""]); state.q = ""; $("search").value = ""; renderTree(); };

  /* ---------- 启动 ---------- */
  renderTree();
  selectDir("demo1/src/main/java/com/rxt/finance");
})();
