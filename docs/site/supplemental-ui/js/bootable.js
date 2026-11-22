;(function () {
  'use strict'

  // The theme: the reader's choice, kept in localStorage, else the system's (see head-meta.hbs).
  var root = document.documentElement
  var themeToggle = document.querySelector('.theme-toggle')
  if (themeToggle) {
    themeToggle.addEventListener('click', function () {
      var dark = root.dataset.theme
        ? root.dataset.theme === 'dark'
        : window.matchMedia('(prefers-color-scheme: dark)').matches
      root.dataset.theme = dark ? 'light' : 'dark'
      try { localStorage.setItem('bootable-theme', root.dataset.theme) } catch (e) {}
    })
  }

  // The default UI's site.js scrolls to an in-page anchor, from a link or the URL on load, to just below the bottom of
  // .toolbar, which it expects to stick under the header. Ours, the breadcrumbs bar, scrolls away with the page, so
  // once the reader has scrolled its bottom is above the viewport, and each jump would overshoot by that much. Give
  // site.js the sticky header's bottom instead, plus the gap that scroll-padding-top leaves (bootable.css).
  var toolbar = document.querySelector('.toolbar')
  var header = document.querySelector('.header')
  if (toolbar && header) {
    toolbar.getBoundingClientRect = function () {
      var bottom = header.getBoundingClientRect().bottom + 24
      return { top: bottom, bottom: bottom, left: 0, right: 0, width: 0, height: 0, x: 0, y: bottom }
    }
  }

  // Links to other sites open in a new tab, so the reader keeps their place in the docs; the page's own links, and the
  // API reference's, which are on this host, don't. Links added later, such as search results, are this site's own.
  Array.prototype.forEach.call(document.querySelectorAll('a[href]'), function (link) {
    if (!/^https?:$/.test(link.protocol) || link.host === window.location.host) return
    link.target = '_blank'
    var rel = (link.getAttribute('rel') || '').split(/\s+/).filter(Boolean)
    if (rel.indexOf('noopener') < 0) rel.push('noopener')
    link.setAttribute('rel', rel.join(' '))
  })

  // The search (the Lunr extension's search-ui.js runs the field, over the docs pages; apiSearch adds the API's): / or
  // Ctrl+K / Cmd+K focuses it.
  var searchInput = document.getElementById('search-input')
  if (searchInput) {
    document.addEventListener('keydown', function (e) {
      var typing = /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement.tagName) || document.activeElement.isContentEditable
      if ((e.key === '/' && !typing) || (e.key === 'k' && (e.ctrlKey || e.metaKey))) {
        e.preventDefault()
        searchInput.focus()
        searchInput.select()
      }
    })
    // search-ui.js searches on keydown only, so pasted or dropped text would find nothing; its debounce makes the extra
    // keydown, after a typed key's own, one search
    searchInput.addEventListener('input', function () {
      searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Unidentified' }))
    })
    var field = searchInput.parentNode
    // The arrow keys move a highlight through the results while the focus stays in the field, and Enter opens the
    // highlighted one, or the first. This listens in the capture phase, ahead of search-ui.js's own keydown listener on the input,
    // and stops these keys there: search-ui.js re-runs the search on any key, which rebuilds the results.
    var activeResult = -1
    function results () {
      return Array.prototype.slice.call(field.querySelectorAll('.search-result-document-hit > a'))
    }
    function highlight (index) {
      var links = results()
      links.forEach(function (link, i) {
        link.classList.toggle('is-active', i === index)
        link.setAttribute('aria-selected', String(i === index))
      })
      activeResult = index
      if (links[index]) links[index].scrollIntoView({ block: 'nearest' })
    }
    field.addEventListener('keydown', function (e) {
      if (e.target !== searchInput) return
      var links = results()
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault()
        e.stopPropagation()
        if (!links.length) return
        var next = e.key === 'ArrowDown' ? activeResult + 1 : activeResult - 1
        highlight(Math.max(-1, Math.min(next, links.length - 1)))
      } else if (e.key === 'Enter') {
        e.stopPropagation()
        var link = links[activeResult] || links[0]
        if (!link) return
        e.preventDefault()
        if (e.ctrlKey || e.metaKey) window.open(link.href)
        else window.location.href = link.href
      }
    }, true)
    // new results: no highlight, until the arrow keys pick one
    new MutationObserver(function () { activeResult = -1 }).observe(field, { childList: true, subtree: true })
    apiSearch(searchInput, field)
  }

  // The API reference's results, as their own group below the docs': matched by name against Dokka's search index
  // (api/scripts/pages.json), loaded the first time the field is used. search-ui.js rebuilds its results panel on each
  // search, so the group is added each time the panel is.
  function apiSearch (input, field) {
    var MAX = 8
    // the page's version's API reference, where the header's API link goes: e.g. bootable/3.1/api/
    var apiLink = document.querySelector('.section-link-api')
    if (!apiLink) return
    var apiIndex = apiLink.href
    var entries
    var loading

    function load () {
      if (loading) return loading
      loading = fetch(new URL('scripts/pages.json', apiIndex))
        .then(function (response) { return response.ok ? response.json() : [] })
        .then(function (pages) { entries = pages.map(entry) })
        .catch(function () { entries = [] })
        .then(function () {
          // a search made while the index loaded
          var dataset = field.querySelector('.search-result-dataset')
          if (dataset) addGroup(dataset)
        })
      return loading
    }

    // name: the simple name; fqn: the qualified name, Dokka's description; signature: Dokka's name
    function entry (page) {
      var keys = page.searchKeys || []
      return {
        name: keys[0] || page.name,
        fqn: page.description || '',
        signature: page.name || '',
        location: page.location,
        haystack: keys.join(' ').toLowerCase(),
      }
    }

    // a type, ahead of its members and functions of the same match
    var TYPE = /^(?:[a-z]+ )*(?:class|interface|object|typealias) /

    function score (e, query, terms) {
      var name = e.name.toLowerCase()
      if (!terms.every(function (t) { return e.haystack.indexOf(t) >= 0 })) return 0
      var type = TYPE.test(e.signature) ? 5 : 0
      if (name === query) return 100 + type
      if (name.indexOf(query) === 0) return 60 + type
      if (name.indexOf(query) >= 0) return 40 + type
      return (e.fqn.toLowerCase().indexOf(query) >= 0 ? 20 : 10) + type
    }

    function find (text) {
      var query = text.trim().toLowerCase()
      if (!entries || !query) return []
      var terms = query.split(/\s+/)
      return entries
        .map(function (e) { return { e: e, s: score(e, query, terms) } })
        .filter(function (r) { return r.s > 0 })
        .sort(function (a, b) { return b.s - a.s || a.e.name.length - b.e.name.length || a.e.fqn.localeCompare(b.e.fqn) })
        .slice(0, MAX)
        .map(function (r) { return r.e })
    }

    function el (tag, className, text) {
      var node = document.createElement(tag)
      if (className) node.className = className
      if (text != null) node.textContent = text
      return node
    }

    function highlighted (text, query) {
      var span = el('span', 'search-result-api-name')
      var at = query ? text.toLowerCase().indexOf(query) : -1
      if (at < 0) {
        span.textContent = text
      } else {
        span.appendChild(document.createTextNode(text.slice(0, at)))
        span.appendChild(el('span', 'search-result-highlight', text.slice(at, at + query.length)))
        span.appendChild(document.createTextNode(text.slice(at + query.length)))
      }
      return span
    }

    // the group, in search-ui.js's markup, so the arrow keys and the styles treat it as more results
    function group (results, query) {
      var item = el('div', 'search-result-item search-result-api')
      item.appendChild(el('div', 'search-result-document-title', 'API reference'))
      results.forEach(function (e) {
        var hit = el('div', 'search-result-document-hit')
        var link = el('a')
        link.href = new URL(e.location, apiIndex).href
        var title = el('div', 'search-result-section-title')
        title.appendChild(highlighted(e.name, query))
        var pkg = e.fqn.slice(0, Math.max(0, e.fqn.length - e.name.length - 1))
        if (pkg) title.appendChild(el('span', 'search-result-api-package', pkg))
        link.appendChild(title)
        link.appendChild(el('code', 'search-result-api-signature', e.signature))
        hit.appendChild(link)
        item.appendChild(hit)
      })
      return item
    }

    function addGroup (dataset) {
      if (dataset.querySelector('.search-result-api')) return
      var results = find(input.value)
      if (!results.length) return
      // search-ui.js's "no results" says nothing about the API's
      var docsHits = dataset.querySelectorAll('.search-result-document-hit > a').length
      if (!docsHits) dataset.innerHTML = ''
      dataset.appendChild(group(results, input.value.trim().toLowerCase()))
    }

    function panel () {
      return field.querySelector('.search-result-dropdown-menu')
    }

    function watch (menu) {
      new MutationObserver(function () {
        var dataset = menu.querySelector('.search-result-dataset')
        if (dataset) addGroup(dataset)
      }).observe(menu, { childList: true })
    }

    // focus doesn't fire in a page that isn't focused itself, so typing loads it too
    input.addEventListener('focus', load, { once: true })
    input.addEventListener('keydown', load, { once: true })
    var menu = panel()
    if (menu) {
      watch(menu)
    } else {
      // search-ui.js adds the panel once it runs
      var waiting = new MutationObserver(function () {
        var added = panel()
        if (added) { waiting.disconnect(); watch(added) }
      })
      waiting.observe(field, { childList: true })
    }
  }

  // The install blocks' Telemetry switch: an install with a telemetry variant has both listings in each tab, and one
  // choice, on <html> as data-install-telemetry (set before the first paint, in head-meta.hbs) and kept in
  // localStorage, shows one in every block on every page. The switch goes at the end of each block's tab bar.
  var TELEMETRY_KEY = 'bootable-install-telemetry'
  var telemetryBlocks = document.querySelectorAll('.install-telemetry')
  if (telemetryBlocks.length) {
    var telemetrySwitches = []
    var setTelemetry = function (on) {
      if (on) root.dataset.installTelemetry = 'on'
      else delete root.dataset.installTelemetry
      telemetrySwitches.forEach(function (button) { button.setAttribute('aria-checked', String(on)) })
    }
    Array.prototype.forEach.call(telemetryBlocks, function (block) {
      var tablist = block.querySelector('.tablist')
      if (!tablist) return
      var button = document.createElement('button')
      button.type = 'button'
      button.className = 'install-switch'
      button.setAttribute('role', 'switch')
      button.title = 'Show the dependencies with or without telemetry'
      button.innerHTML = '<span class="install-switch-track" aria-hidden="true"></span><span>Telemetry</span>'
      button.addEventListener('click', function () {
        var on = root.dataset.installTelemetry !== 'on'
        // keep the block where it is on the screen, as the other listing, maybe longer, takes its place
        var top = block.getBoundingClientRect().top
        setTelemetry(on)
        window.scrollBy(0, block.getBoundingClientRect().top - top)
        try { localStorage.setItem(TELEMETRY_KEY, on ? 'on' : 'off') } catch (e) {}
      })
      tablist.appendChild(button)
      telemetrySwitches.push(button)
    })
    setTelemetry(root.dataset.installTelemetry === 'on')
  }

  // The docs version: the header's switch goes to the chosen version of this page, or to its start page where this
  // page isn't in it (header-content.hbs).
  var versionSwitch = document.getElementById('version-switch')
  if (versionSwitch) {
    versionSwitch.addEventListener('change', function () {
      var option = versionSwitch.options[versionSwitch.selectedIndex]
      if (option && option.dataset.href) window.location.href = option.dataset.href
    })
  }

  // The wiring style: one choice, which tabs.js keeps in localStorage under bootable-tabs-di. The header's switch, the
  // DI tabs, the home page's cards and the wiring page's styles all show and set it.
  var STORAGE_KEY = 'bootable-tabs-di'
  var select = document.getElementById('di-switch')
  if (!select) return
  var styles = Array.prototype.map.call(select.options, function (o) { return o.value })

  function stored () {
    try { return localStorage.getItem(STORAGE_KEY) } catch (e) { return null }
  }

  function store (style) {
    try { localStorage.setItem(STORAGE_KEY, style) } catch (e) {}
  }

  function diTabs (name) {
    return Array.prototype.filter.call(
      document.querySelectorAll('.tabs[data-sync-group-id="di"] .tablist li.tab'),
      function (tab) { return tab.textContent.trim() === name }
    )
  }

  // the wiring page: a card per style, its id the style's name in lower case, with a button to choose it
  var styleCards = Array.prototype.filter.call(document.querySelectorAll('.wiring-style[id]'), function (card) {
    return styleOf(card.id)
  })

  function styleOf (id) {
    return styles.filter(function (style) { return style.toLowerCase() === id })[0]
  }

  styleCards.forEach(function (card) {
    var style = styleOf(card.id)
    var button = document.createElement('button')
    button.type = 'button'
    button.className = 'wiring-style-choose'
    button.addEventListener('click', function () { choose(style) })
    var title = card.querySelector(':scope > .title')
    ;(title || card).appendChild(button)
  })

  function show (style) {
    select.value = style
    styleCards.forEach(function (card) {
      var chosen = styleOf(card.id) === style
      var button = card.querySelector('.wiring-style-choose')
      card.classList.toggle('is-chosen', chosen)
      button.textContent = chosen ? 'Your style' : 'Use ' + styleOf(card.id)
      button.disabled = chosen
      button.setAttribute('aria-pressed', String(chosen))
    })
  }

  function choose (style) {
    var tab = diTabs(style)[0]
    // selecting one tab of the group selects it in the others, and stores the choice
    if (tab) tab.click()
    else store(style)
    show(style)
  }

  show(stored() || styles[0])

  select.addEventListener('change', function () { choose(select.value) })

  document.addEventListener('click', function (e) {
    if (!e.target.closest) return
    var tab = e.target.closest('.tabs[data-sync-group-id="di"] .tablist li.tab')
    if (tab) show(tab.textContent.trim())
    // a card named for a wiring style that links to the wiring page, as on the home page, chooses it; the page opens at
    // its top, with the chosen style marked
    var card = e.target.closest('.cards a[href$="wiring.html"]')
    if (card) {
      var style = card.textContent.trim()
      if (styles.indexOf(style) >= 0) {
        store(style)
        show(style)
      }
    }
  })
})()
