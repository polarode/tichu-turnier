package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.Tournament

interface ListTournaments {
    /** Lists only the tournaments owned by [owner]; another admin's never appear. */
    fun list(owner: AdminUserId): List<Tournament>
}
