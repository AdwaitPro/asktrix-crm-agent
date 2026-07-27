package com.asktrix.agent.core.database

import android.content.Context
import com.asktrix.agent.core.security.crypto.KeystoreCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erases every trace of customer data from the device.
 *
 * §3 requires the local store to be purged on logout, on integrity failure, and on remote wipe.
 * Two layers, because either alone is incomplete:
 *
 *  1. Delete the database file. Removes the data.
 *  2. Destroy the Keystore key. Renders any residue - a journal file, a copy in an unallocated
 *     block, a forensic image taken earlier - permanently undecryptable, because the key it was
 *     encrypted under no longer exists in hardware.
 *
 * Step 2 is what makes the purge meaningful on a device an attacker may physically hold.
 */
@Singleton
class CachePurger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: KeystoreCrypto,
) {

    fun purge() {
        val database = context.getDatabasePath(AsktrixDatabase.NAME)
        listOf(database, File(database.path + "-wal"), File(database.path + "-shm")).forEach {
            runCatching { if (it.exists()) it.delete() }
        }
        crypto.destroyKey()
    }
}

private typealias File = java.io.File
