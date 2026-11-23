'use strict'

// Writes the docs' module tables and install snippets from docs/modules.yml, so they follow the build rather than
// being written by hand. Before the pages are converted, it expands these lines, each on a line of its own, in a page's
// AsciiDoc source:
//
//   modules::<function>[]      the function's modules, in its groups, as a table
//   modules::all[]             every function's table, for the catalogue
//   install::[<module>, ...]   the dependencies to add, in a tab for each wiring style
//   install::<configuration>[<module>, ...]   the same, in another configuration, such as testImplementation
//
// A module's -kodein, -koin, -metro, and -inara flavours are those the build publishes: the build gives a module a flavour
// when it has that flavour's sources, such as src/kodein/kotlin, so this checks the same. A module without a
// flavour is added under its own name in that wiring style's tab.
//
// The build warns when settings.gradle.kts includes a module that modules.yml doesn't list, or the other way round, so
// the docs can't fall behind the modules.

const fs = require('node:fs')
const path = require('node:path')
const yaml = require('js-yaml')

const STYLES = [
  { tab: 'Vanilla' },
  { tab: 'Kodein', flavour: 'kodein' },
  { tab: 'Koin', flavour: 'koin' },
  { tab: 'Metro', flavour: 'metro', plugin: 'plugins {\n  id("dev.zacsweers.metro") version "{metro-version}"\n}\n\n' },
  {
    tab: 'Inara',
    flavour: 'inara',
    plugin: 'plugins {\n  id("com.google.devtools.ksp") version "{ksp-version}"\n}\n\n',
    // Inara's processor generates the builders of the application's own modules
    processor: '  ksp("org.kodein.di.inara:inara-processor:{inara-version}")',
  },
]

const CHOICES = { one: 'choose one', optional: 'optional', implied: 'comes with the others' }

const MODULES_LINE = /^modules::([\w-]+)\[\]$/
const INSTALL_LINE = /^install::(\w*)\[([^\]]*)\]$/

module.exports.register = function ({ config = {}, playbook }) {
  const logger = this.getLogger('modules-extension')
  const root = path.resolve(playbook.dir, config.root || '../..')
  const file = path.resolve(root, config.file || 'docs/modules.yml')
  const { functions } = yaml.load(fs.readFileSync(file, 'utf8'))

  // every module by name, its -otel variant included, with its function and group
  const modules = new Map()
  for (const fn of functions) {
    for (const group of fn.groups) {
      for (const module of group.modules) {
        // an implied module's -otel variant is added alongside it, so it belongs in an optional group of its own
        if (module.otel && group.choice === 'implied') {
          logger.warn(`modules.yml: ${module.name} comes with another module, so list ${module.name}-otel in an optional group, not under otel:`)
        }
        for (const name of [module.name, module.otel && `${module.name}-otel`].filter(Boolean)) {
          if (modules.has(name)) logger.warn(`${name} is in modules.yml twice`)
          modules.set(name, { module, fn, group })
        }
      }
    }
  }

  const flavours = (name) => STYLES.map((s) => s.flavour)
    .filter((flavour) => flavour && fs.existsSync(path.join(root, name, 'src', flavour, 'kotlin')))

  checkAgainstBuild(root, modules, logger)

  this.on('contentClassified', ({ contentCatalog }) => {
    for (const page of contentCatalog.getPages((page) => page.out && page.mediaType === 'text/asciidoc')) {
      const source = page.contents.toString()
      if (!/^(modules|install)::/m.test(source)) continue
      const at = `${page.src.component}:${page.src.module}:${page.src.relative}`
      const lines = source.split('\n').map((line) => {
        let match
        if ((match = line.match(MODULES_LINE))) {
          const id = match[1]
          if (id === 'all') return functions.map((fn) => table(fn, page, true)).join('\n')
          const fn = functions.find((f) => f.id === id)
          if (fn) return table(fn, page, false)
          logger.warn(`${at}: modules::${id}[]: no function ${id} in modules.yml`)
        } else if ((match = line.match(INSTALL_LINE))) {
          const names = match[2].split(',').map((name) => name.trim()).filter(Boolean)
          for (const name of names.filter((name) => !modules.has(name))) {
            logger.warn(`${at}: install::[]: no module ${name} in modules.yml`)
          }
          return install(names, telemetryVariant(names, functions, modules), flavours, match[1] || 'implementation')
        }
        return line
      })
      page.contents = Buffer.from(lines.join('\n'))
    }
  })
}

// a function's modules, a group to a row of their own; withTitle, for the catalogue, puts the table in a section
// for the function, which links to its page
function table (fn, page, withTitle) {
  const here = `${page.src.module}:${page.src.relative}`
  const out = []
  if (withTitle) {
    out.push(`== ${fn.title}`, '')
    if (fn.page) out.push(`See xref:${fn.page}[].`, '')
  }
  out.push('[.modules,cols="2,3",options="header"]', '|===', '| Module | What it provides', '')
  for (const group of fn.groups) {
    if (group.title) {
      const choice = group.note || CHOICES[group.choice]
      out.push(`2+h| ${group.title}${choice ? `: ${choice}` : ''}`)
    }
    for (const module of group.modules) {
      const name = `\`${module.name}\``
      const linked = module.page && module.page.split('#')[0] !== here ? `xref:${module.page}[${name}]` : name
      const otel = module.otel ? ' [.module-variant]#(`-otel`)#' : ''
      out.push(`| ${linked}${otel}`)
      const telemetry = module.otel ? ` +\n${variant(module)} ${module.otel}`
        : module.traced ? ` +\n[.module-variant]#With telemetry:# ${module.traced}` : ''
      out.push(`| ${module.provides}${telemetry}`)
      out.push('')
    }
  }
  out.push('|===', '')
  return out.join('\n')
}

// a module's -otel variant, which the application uses in its place
function variant (module) {
  return `[.module-variant]#Or \`${module.name}-otel\`,# with telemetry:`
}

// The install with telemetry: each module swapped for its -otel variant, where it has one; each module that extends
// a module in the install added, the base listed or brought in by a listed module (its implied group's with:); and
// the SDK, boot-telemetry-otel, if nothing in the install brings it, which is all a module that's traced: needs. The
// same names, if none of that applies.
function telemetryVariant (names, functions, modules) {
  const present = new Set(names)
  for (const fn of functions) {
    for (const group of fn.groups) {
      if (!group.with) continue
      const bringers = fn.groups.find((g) => g.title === group.with)
      if (bringers && bringers.modules.some((m) => present.has(m.name))) group.modules.forEach((m) => present.add(m.name))
    }
  }
  const out = names.map((name) => {
    const entry = modules.get(name)
    return entry && entry.module.name === name && entry.module.otel ? `${name}-otel` : name
  })
  for (const { module, fn } of modules.values()) {
    if (!module.extends || !present.has(module.extends) || out.includes(module.name)) continue
    // after the last listed module of its function, so it sits with the modules it goes with
    const last = out.reduce((at, name, i) => (modules.get(name) || {}).fn === fn ? i : at, out.length - 1)
    out.splice(last + 1, 0, module.name)
  }
  const traced = out.some((name) => (modules.get(name) || {}).module && modules.get(name).module.name === name && modules.get(name).module.traced)
  if (!traced && out.every((name, i) => name === names[i]) && out.length === names.length) return names
  if (!out.some((name) => name.startsWith('boot-telemetry-otel'))) out.push('boot-telemetry-otel')
  return out
}

// the dependencies, in a tab for each wiring style: each module's flavour for that style, where it has one. With a
// telemetry variant that differs, each tab has both, and the reader's Telemetry switch (bootable.js) shows one.
function install (names, otelNames, flavours, configuration) {
  const variants = otelNames === names ? [[names]] : [[names, 'install-plain'], [otelNames, 'install-otel']]
  const listing = (style, list, role) => {
    const flavoured = list.map((name) => style.flavour && flavours(name).includes(style.flavour) ? `${name}-${style.flavour}` : name)
    const anyFlavoured = list.some((name) => flavours(name).includes(style.flavour))
    const plugin = style.plugin && anyFlavoured ? style.plugin : ''
    const processor = style.processor && anyFlavoured && configuration === 'implementation' ? [style.processor] : []
    return [
      `[source,kotlin,subs=attributes+${role ? `,role=${role}` : ''}]`,
      '----',
      `${plugin}dependencies {`,
      ...flavoured.map((name) => `  ${configuration}("{bootable-group}:${name}:{bootable-version}")`),
      ...processor,
      '}',
      '----',
    ].join('\n')
  }
  const tabs = STYLES.map((style) =>
    [`${style.tab}::`, '+', variants.map(([list, role]) => listing(style, list, role)).join('\n+\n')].join('\n'))
  const block = ['[tabs,sync-group-id=di]', '======', tabs.join('\n\n'), '======'].join('\n')
  return variants.length === 1 ? block : ['[.install-telemetry]', '--', block, '--'].join('\n')
}

// the published modules, from settings.gradle.kts, against modules.yml
function checkAgainstBuild (root, modules, logger) {
  const settings = path.join(root, 'settings.gradle.kts')
  if (!fs.existsSync(settings)) return
  const included = new Set([...fs.readFileSync(settings, 'utf8').matchAll(/include\("(boot[^":]*)"\)/g)].map((m) => m[1]))
  for (const name of included) {
    if (!modules.has(name)) logger.warn(`modules.yml: ${name} is in the build, but not in modules.yml`)
  }
  for (const name of modules.keys()) {
    if (!included.has(name)) logger.warn(`modules.yml: ${name} is not in the build`)
  }
}
