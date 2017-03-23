# NotifBot FAQ

## How do I add an app to NotifBot?

Basically, share the app from Google Play to NotifBot.
Here are the detailed steps:

 1. Go to the Google Play page of the app you want to add
    ([example](https://play.google.com/store/apps/details?id=com.smartthings.android))
 1. Find the Share button (last time I checked, it's right below the reviews)
 1. Choose NotifBot in the Share menu
 1. You should see a Toast afterwards as confirmation

## After playing the notification Android Auto asks me for reply?

Due to Android Auto's restriction,
only notifications that you can reply will be shown.
But we can't really reply the notifications.
You can just ignore the reply request.
If you do reply to the notification,
that goes nowhere.

## The NotifBot notifications will show even without Android Auto

Due to Android's restriction,
we cannot reliably detect whether Android Auto is running.
As a result,
we just show NotifBot notifications regardless the status of Android Auto.
The NotifBot notification will dismiss itself when the original notification is
gone, or you can just swipe it away if Android Auto is not running.

If you know a way to reliably detect current running app, please let us know!
