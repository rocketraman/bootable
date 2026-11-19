package com.github.rocketraman.bootable.leader

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.yield

val LeaderElectionTest by testSuite {
  test("AlwaysLeader is the leader") {
    assert(AlwaysLeader.isLeader())
    AlwaysLeader.awaitLeadership()
  }

  test("AlwaysLeader has one term") {
    assert(AlwaysLeader.fencingToken == 0L)
    assert(AlwaysLeader.requireFencingToken() == 0L)
  }

  test("requireFencingToken throws when this instance doesn't lead") {
    val election = object : LeaderElection {
      override val leadership = MutableStateFlow(false)
      override val fencingToken: Long? = null
    }
    assert(runCatching { election.requireFencingToken() }.exceptionOrNull() is NotLeaderException)
  }

  test("awaitLeadership returns once this instance leads") {
    val state = MutableStateFlow(false)
    val election = object : LeaderElection {
      override val leadership = state
      override val fencingToken get() = if (state.value) 1L else null
    }
    val waiting = async { election.awaitLeadership() }
    yield()
    assert(!waiting.isCompleted)
    state.value = true
    waiting.await()
    assert(election.isLeader())
  }
}
