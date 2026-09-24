package com.openmausbot.companion.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class TeamManagementDraftTest {
    @Test
    fun botCreationPreservesUnsavedMembershipEdits() {
        val draft = TeamManagementDraft()
        draft.selectedState.value = "Planning"
        draft.nameDraftState.value = "Planning new name"
        draft.originalIdsState.value = setOf("existing")
        draft.pickedIdsState.value = setOf("candidate")

        draft.includeCreatedBot("new")

        assertEquals("Planning", draft.selectedState.value)
        assertEquals("Planning new name", draft.nameDraftState.value)
        assertEquals(setOf("existing", "new"), draft.originalIdsState.value)
        assertEquals(setOf("candidate", "new"), draft.pickedIdsState.value)
    }
}
