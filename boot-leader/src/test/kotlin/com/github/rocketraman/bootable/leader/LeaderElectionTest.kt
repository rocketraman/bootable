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

  test("awaitLeadership returns once this instance leads") {
    val state = MutableStateFlow(false)
    val election = object : LeaderElection {
      override val leadership = state
    }
    val waiting = async { election.awaitLeadership() }
    yield()
    assert(!waiting.isCompleted)
    state.value = true
    waiting.await()
    assert(election.isLeader())
  }
}
