package com.asktrix.agent.core.database.di

import android.content.Context
import androidx.room.Room
import com.asktrix.agent.core.database.AsktrixDatabase
import com.asktrix.agent.core.database.dao.CallRecordDao
import com.asktrix.agent.core.database.dao.ClientDao
import com.asktrix.agent.core.database.dao.OutboxDao
import com.asktrix.agent.core.database.dao.TimelineDao
import com.asktrix.agent.core.security.crypto.KeystoreCrypto
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.security.SecureRandom
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Room backed by SQLCipher.
     *
     * The passphrase is 32 random bytes generated once, then stored encrypted under a hardware-backed
     * Keystore key. So the database file is encrypted with a key that is itself protected by the
     * secure element — copying the file off a rooted device yields nothing usable.
     *
     * `net.zetetic:sqlcipher-android` is the current artifact; the older
     * `android-database-sqlcipher` coordinate is superseded.
     */
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
        crypto: KeystoreCrypto,
    ): AsktrixDatabase {
        System.loadLibrary("sqlcipher")

        val passphrase = loadOrCreatePassphrase(context, crypto)
        val factory = SupportOpenHelperFactory(passphrase)

        return Room.databaseBuilder(context, AsktrixDatabase::class.java, AsktrixDatabase.NAME)
            .openHelperFactory(factory)
            // No fallbackToDestructiveMigration: silently dropping the cache would hide a real
            // migration bug. Migrations are written explicitly and tested.
            .build()
    }

    private fun loadOrCreatePassphrase(context: Context, crypto: KeystoreCrypto): ByteArray {
        val file = File(context.filesDir, PASSPHRASE_FILE)
        if (file.exists()) {
            crypto.decrypt(file.readText())?.let { return it.hexToBytes() }
            // Undecryptable means the Keystore key was destroyed — by a purge, a factory reset, or
            // tampering. The old database is unreadable regardless, so start clean.
            file.delete()
            context.getDatabasePath(AsktrixDatabase.NAME).delete()
        }
        val bytes = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        file.writeText(crypto.encrypt(bytes.toHex()))
        return bytes
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(RADIX_HEX).toByte() }.toByteArray()

    @Provides fun clientDao(db: AsktrixDatabase): ClientDao = db.clientDao()
    @Provides fun timelineDao(db: AsktrixDatabase): TimelineDao = db.timelineDao()
    @Provides fun callRecordDao(db: AsktrixDatabase): CallRecordDao = db.callRecordDao()
    @Provides fun outboxDao(db: AsktrixDatabase): OutboxDao = db.outboxDao()

    private const val PASSPHRASE_FILE = "db_passphrase.bin"
    private const val PASSPHRASE_BYTES = 32
    private const val RADIX_HEX = 16
}
