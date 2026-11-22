'use strict'

// The site's versions: `next`, from the checkout being built, and one version per minor release, from the newest patch
// tag of that minor release whose tree has the docs (docs/antora.yml), such as v3.1.2 for version 3.1.
//
// The playbook's release content source lists no tags of its own: this fills them in once the playbook is built, so a
// release only has to be tagged to appear. Antora maps a tag to its version with the source's version pattern; a
// version per patch release would make each fix its own entry in the switcher, and two tags of one minor release would
// collide as the same version, so only the newest is taken.
//
// `next` is marked a prerelease, so the newest release, not the work in progress, is the site's default version.
// antora.yml can't say so itself: it is the same file at every tag.
//
// Each version's bootable-version attribute, which the install snippets use, is its release: 3.1.2 for the 3.1 docs,
// from tag v3.1.2, and, for next, the checkout's development version, such as 3.1.0-SNAPSHOT, from build.gradle.kts,
// since next documents modules and APIs that no release has yet.

const { execFileSync } = require('node:child_process')
const fs = require('node:fs')
const path = require('node:path')

const RELEASE_TAG = /^v(\d+)\.(\d+)\.(\d+)$/
const DEVELOPMENT_VERSION = /^val developmentVersion = "([^"]+)"$/m

module.exports.register = function ({ config = {} }) {
  const logger = this.getLogger('versions-extension')
  const prerelease = config.prerelease || 'next'
  let developmentVersion

  this.once('playbookBuilt', ({ playbook }) => {
    const sources = playbook.content.sources
    // next's checkout, whose build.gradle.kts says what it publishes
    const next = sources.find((source) => source.version === prerelease)
    if (next) developmentVersion = readDevelopmentVersion(path.resolve(playbook.dir, next.url), logger)
    const releases = sources.find((source) => source.tags && source.tags.length === 0 && source.version)
    if (!releases) return
    const repo = path.resolve(playbook.dir, releases.url)
    const tags = releaseTags(repo)
    logger.info(tags.length ? `release versions from ${tags.join(', ')}` : 'no release has the docs yet: building next only')
    if (tags.length) {
      releases.tags = tags
    } else {
      // a source with no refs to read is an error to Antora: leave it out until there is a release
      sources.splice(sources.indexOf(releases), 1)
    }
    this.updateVariables({ playbook })
  })

  this.on('contentAggregated', ({ contentAggregate }) => {
    for (const aggregate of contentAggregate) {
      const version = aggregate.version === prerelease
        ? developmentVersion
        : (aggregate.origins || [])[0]?.refname?.replace(/^v/, '')
      if (aggregate.version === prerelease) aggregate.prerelease = true
      if (version) {
        const asciidoc = (aggregate.asciidoc ||= {})
        const attributes = (asciidoc.attributes ||= {})
        attributes['bootable-version'] = version
      }
    }
  })
}

// the newest patch tag of each minor release that has the docs, newest release first
function releaseTags (repo) {
  const newest = new Map()
  for (const tag of git(repo, 'tag', '--list', 'v*').split('\n')) {
    const match = tag.match(RELEASE_TAG)
    if (!match) continue
    const [, major, minor, patch] = match.map(Number)
    const key = `${major}.${minor}`
    const current = newest.get(key)
    if (current && current.patch >= patch) continue
    newest.set(key, { tag, major, minor, patch })
  }
  return [...newest.values()]
    .filter(({ tag }) => hasDocs(repo, tag))
    .sort((a, b) => b.major - a.major || b.minor - a.minor)
    .map(({ tag }) => tag)
}

// the version an untagged build publishes, build.gradle.kts's developmentVersion, such as 3.1.0-SNAPSHOT
function readDevelopmentVersion (repo, logger) {
  const match = fs.readFileSync(path.join(repo, 'build.gradle.kts'), 'utf8').match(DEVELOPMENT_VERSION)
  if (!match) logger.warn('no developmentVersion in build.gradle.kts: next\'s install snippets have no version')
  return match && match[1]
}

function hasDocs (repo, tag) {
  try {
    git(repo, 'cat-file', '-e', `${tag}:docs/antora.yml`)
    return true
  } catch (e) {
    return false
  }
}

function git (repo, ...args) {
  return execFileSync('git', args, { cwd: repo, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim()
}
