<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Existing permissions... -->

    <!-- Permissions needed for lock state detection -->
    <!-- Permission to read lock screen state via KeyguardManager -->
    <!-- This is a normal permission, automatically granted, no user dialog needed -->
    <!-- Implicit permission via KeyguardManager usage, explicit declaration for clarity -->
    <uses-permission android:name="android.permission.DISABLE_KEYGUARD" />

    <!-- Application configuration continues below... -->

</manifest>
