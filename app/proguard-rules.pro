# DateNote currently does not require custom shrinking rules.
# WorkManager restores scheduled workers by class name after process death.
-keep class com.datenote.app.reminder.ScheduleReminderWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep kotlinx.serialization DTOs and generated serializers used by Ktor.
-keep @kotlinx.serialization.Serializable class com.datenote.app.data.remote.** { *; }
-keep class com.datenote.app.data.remote.**$$serializer { *; }
