package com.rplab.kaminarinfty.model

/**
 * Everything the live/poll stream can change in local history.
 *
 * Kept separate from [NftyMessage] because [Removed] and [TopicCleared] are
 * server lifecycle events (ntfy `message_delete` / `message_clear`), not
 * notifications the user should see in the inbox.
 */
sealed interface StreamUpdate {
    data class NewMessage(val message: NftyMessage) : StreamUpdate

    /** ntfy `message_delete`: a single message was deleted server-side. */
    data class Removed(val messageId: String) : StreamUpdate

    /** ntfy `message_clear`: every message in a topic (or all topics) was cleared. */
    data class TopicCleared(val topic: String?) : StreamUpdate
}
