/*
 * Copyright 2010 Rodrigo Damazio <rodrigo@damazio.org>
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.damazio.notifier.service;

import java.util.List;

import org.damazio.notifier.NotifierConstants;
import org.damazio.notifier.NotifierMain;
import org.damazio.notifier.NotifierPreferences;
import org.damazio.notifier.R;
import org.damazio.notifier.command.CommandService;
import org.damazio.notifier.notification.Notification;
import org.damazio.notifier.notification.NotificationService;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

/**
 * Service which listens for relevant events and sends notifications about
 * them.
 *
 * @author Rodrigo Damazio
 */
public class NotifierService extends Service {
 
  /**
   * If a {@link Notification} object is passed as this extra when starting
   * the service, that notification will be sent.
   */
  private static final String EXTRA_NOTIFICATION = "org.damazio.notifier.service.EXTRA_NOTIFICATION";

  private NotifierPreferences preferences;
  private ServicePreferencesListener preferenceListener;
  private NotificationService notificationService;
  private CommandService commandService;

  private boolean started = false;

  /**
   * Listener for changes to the preferences.
   */
  private class ServicePreferencesListener
      implements SharedPreferences.OnSharedPreferenceChangeListener {
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
      if (key.equals(getString(R.string.notifications_enabled_key)) ||
          key.equals(getString(R.string.command_enable_key))) {
        updateServiceState();
      } else if (key.equals(getString(R.string.show_notification_icon_key))) {
        showOrHideLocalNotification();
      }
    }
  }

  @Override
  public void onStart(Intent intent, int startId) {
    super.onStart(intent, startId);


    synchronized (this) {
      if (!started) {
        started = true;

        preferences = new NotifierPreferences(this);

        if (!updateServiceState()) {
          return;
        }
  
        if (preferenceListener == null) {
          preferenceListener = new ServicePreferencesListener();
          preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        }
  
        showOrHideLocalNotification();
      }

      sendIntentNotification(intent);
    }
  }

  @Override
  public void onDestroy() {
    Log.i(NotifierConstants.LOG_TAG, "Notifier service going down.");

    synchronized (this) {
      started = false;

      if (preferenceListener != null) {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
      }

      stopNotificationService();
      stopCommandService();

      hideLocalNotification();
    }

    super.onDestroy();
  }

  /**
   * Updates the service state according to preferences.
   * 
   * @return true if the service will keep running, false otherwise
   */
  private boolean updateServiceState() {
    synchronized (this) {
      boolean notificationsEnabled = preferences.areNotificationsEnabled();
      boolean commandsEnabled = preferences.isCommandEnabled();
      if (!notificationsEnabled && !commandsEnabled) {
        stopSelf();
        return false;
      }
  
      if (notificationsEnabled) {
        startNotificationService();
      } else {
        stopNotificationService();
      }
  
      if (commandsEnabled) {
        startCommandService();
      } else {
        stopCommandService();
      }
    }
    return true;
  }

  private void startNotificationService() {
    synchronized (this) {
      if (notificationService == null) {
        notificationService = new NotificationService(this, preferences);
        notificationService.start();
      }
    }
  }

  private void startCommandService() {
    synchronized (this) {
      commandService = new CommandService(this, preferences);
      commandService.start();
    }
  }

  /**
   * If the given intent carries a bundled notification in its extras, sends it.
   */
  private void sendIntentNotification(Intent intent) {
    Notification notification = intent.getParcelableExtra(EXTRA_NOTIFICATION);
    synchronized (this) {
      if (notification != null && notificationService != null) {
        notificationService.sendNotification(notification);
      }
    }
  }

  private void stopCommandService() {
    synchronized (this) {
      if (commandService != null) {
        commandService.shutdown();
        commandService = null;
      }
    }
  }

  private void stopNotificationService() {
    synchronized (this) {
      if (notificationService != null) {
        notificationService.shutdown();
        notificationService = null;
      }
    }
  }

  @Override
  public IBinder onBind(Intent arg0) {
	return null;
  }

  // Notification bar notification

  /**
   * Shows or hides the local notification, according to the user's preference.
   */
  private void showOrHideLocalNotification() {
    // If enabled, show a notification
    if (preferences.isServiceNotificationEnabled()) {
      showLocalNotification();
    } else {
      hideLocalNotification();
    }
  }

  /**
   * Shows the local status bar notification.
   */
  private void showLocalNotification() {
    android.app.Notification notification = createLocalNotification(this);

    NotificationManager notificationManager =
        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    notificationManager.notify(R.string.notification_icon_text, notification);
  }

  private static android.app.Notification createLocalNotification(Context context) {
    String notificationText = context.getString(R.string.notification_icon_text);
    android.app.Notification notification = new android.app.Notification(
        R.drawable.icon, notificationText, System.currentTimeMillis());
    PendingIntent intent = PendingIntent.getActivity(
        context, 0,
        new Intent(context, NotifierMain.class),
        Intent.FLAG_ACTIVITY_NEW_TASK);
    notification.setLatestEventInfo(context,
        context.getString(R.string.app_name),
        notificationText,
        intent);

    notification.flags = android.app.Notification.FLAG_NO_CLEAR
                       | android.app.Notification.FLAG_ONGOING_EVENT;
    return notification;
  }

  /**
   * Hides the local status bar notification.
   */
  private void hideLocalNotification() {
    NotificationManager notificationManager =
        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    notificationManager.cancel(R.string.notification_icon_text);
  }

  // Service control utilities.

  /**
   * Starts the service in the given context.
   */
  public static void start(Context context) {
    context.startService(new Intent(context, NotifierService.class));
  }

  /**
   * Sends the given notification, first starting the service if necessary.
   */
  public static void startAndSend(Context context, Notification notification) {
    Intent intent = new Intent(context, NotifierService.class);
    intent.putExtra(EXTRA_NOTIFICATION, notification);
    context.startService(intent);
  }

  /**
   * Uses the given context to determine whether the service is already running.
   */
  public static boolean isRunning(Context context) {
    ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
    List<RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);

    for (RunningServiceInfo serviceInfo : services) {
      ComponentName componentName = serviceInfo.service;
      String serviceName = componentName.getClassName();
      if (serviceName.equals(NotifierService.class.getName())) {
        return true;
      }
    }

    return false;
  }

  /**
   * Stops the service in the given context.
   */
  public static void stop(Context context) {
    context.stopService(new Intent(context, NotifierService.class));
  }
}
