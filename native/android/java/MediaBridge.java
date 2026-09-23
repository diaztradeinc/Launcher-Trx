package com.diaztradeinc.trxlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.Rating;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.List;

public class MediaBridge extends NotificationListenerService {
    private static volatile MediaBridge instance;
    private static volatile MediaController controller;
    private static volatile MediaController.Callback callback;
    public static volatile String title = "NO TRACK SELECTED";
    public static volatile String artist = "Choose a media app";
    public static volatile String source = "";
    public static volatile Bitmap artwork;
    public static volatile boolean playing;
    public static volatile boolean liked;
    public static volatile long durationMs;
    public static volatile String[] queueTitles=new String[0];
    public static volatile String[] queueArtists=new String[0];
    public static volatile Bitmap[] queueArtwork=new Bitmap[0];
    private static volatile long positionMs;
    private static volatile long positionCapturedAt;
    private static final java.util.Map<String,Bitmap> artworkCache=java.util.Collections.synchronizedMap(
        new java.util.LinkedHashMap<String,Bitmap>(64,.75f,true){
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<String,Bitmap> eldest){return size()>64;}
        });
    private static final java.util.Set<String> artworkRequests=java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static final java.util.concurrent.ExecutorService artworkExecutor=java.util.concurrent.Executors.newFixedThreadPool(2);

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        refresh(this);
    }

    @Override public void onListenerDisconnected() {
        instance = null;
        detachController();
        super.onListenerDisconnected();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) { refresh(this); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { refresh(this); }

    public static boolean hasAccess(Context context) {
        try {
            String enabled = Settings.Secure.getString(
                context.getContentResolver(),"enabled_notification_listeners");
            return enabled != null &&
                enabled.toLowerCase().contains(context.getPackageName().toLowerCase());
        } catch (Throwable ignored) { return false; }
    }

    public static void ensureConnected(Context context) {
        if (!hasAccess(context)) return;
        try {
            NotificationListenerService.requestRebind(
                new ComponentName(context,MediaBridge.class));
        } catch (Throwable ignored) { }
        refresh(context);
    }

    public static void requestAccess(Context context) {
        try {
            Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        } catch (Throwable ignored) {
            try {
                Intent i = new Intent(Settings.ACTION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            } catch (Throwable ignoredAgain) { }
        }
    }

    public static void refresh(Context context) {
        if (!hasAccess(context)) {
            title = "MEDIA ACCESS REQUIRED";
            artist = "Tap play and enable TRX Media Controls";
            artwork = null;
            playing = false;
            durationMs = 0;
            positionMs = 0;
            return;
        }
        try {
            MediaSessionManager manager =
                (MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> controllers = manager.getActiveSessions(
                new ComponentName(context,MediaBridge.class));
            MediaController best = null;
            for (MediaController item : controllers) {
                PlaybackState state = item.getPlaybackState();
                if (best == null) best = item;
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    best = item;
                    break;
                }
            }
            attachController(best);
            updateMetadata(best);
        } catch (SecurityException error) {
            title = "MEDIA ACCESS REQUIRED";
            artist = "Enable TRX Media Controls";
            artwork = null;
        } catch (Throwable ignored) { }
    }

    private static void attachController(MediaController next) {
        if (controller == next) return;
        detachController();
        controller = next;
        if (next == null) return;
        callback = new MediaController.Callback() {
            @Override public void onMetadataChanged(MediaMetadata metadata) {
                updateMetadata(controller);
            }
            @Override public void onPlaybackStateChanged(PlaybackState state) {
                updateMetadata(controller);
            }
            @Override public void onQueueChanged(List<android.media.session.MediaSession.QueueItem> queue) {
                updateQueue(controller);
            }
            @Override public void onSessionDestroyed() {
                controller = null;
                title = "NO TRACK SELECTED";
                artist = "Choose a media app";
                artwork = null;
                playing = false;
            }
        };
        try { next.registerCallback(callback); } catch (Throwable ignored) { }
    }

    private static void detachController() {
        try { if (controller != null && callback != null) controller.unregisterCallback(callback); }
        catch (Throwable ignored) { }
        controller = null;
        callback = null;
    }

    private static void updateMetadata(MediaController active) {
        if (active == null) {
            title = "NO ACTIVE MEDIA SESSION";
            artist = "Start music, then return here";
            source = "";
            artwork = null;
            playing = false;
            queueTitles=new String[0];queueArtists=new String[0];queueArtwork=new Bitmap[0];
            return;
        }
        try {
            source = active.getPackageName();
            PlaybackState state = active.getPlaybackState();
            playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
            positionMs = state == null ? 0 : Math.max(0,state.getPosition());
            positionCapturedAt = android.os.SystemClock.elapsedRealtime();
            MediaMetadata metadata = active.getMetadata();
            if (metadata == null) {
                title = "WAITING FOR TRACK INFO";
                artist = source;
                artwork = null;
                return;
            }
            durationMs = Math.max(0,metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
            try {
                Rating rating=metadata.getRating(MediaMetadata.METADATA_KEY_USER_RATING);
                if(rating!=null&&rating.isRated()){
                    if(rating.getRatingStyle()==Rating.RATING_HEART)liked=rating.hasHeart();
                    else if(rating.getRatingStyle()==Rating.RATING_THUMB_UP_DOWN)liked=rating.isThumbUp();
                }
            } catch(Throwable ignored) { }
            String nextTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (nextTitle == null || nextTitle.trim().isEmpty())
                nextTitle = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            String nextArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (nextArtist == null || nextArtist.trim().isEmpty())
                nextArtist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            if (nextArtist == null || nextArtist.trim().isEmpty())
                nextArtist = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
            title = nextTitle == null || nextTitle.trim().isEmpty() ? "UNKNOWN TRACK" : nextTitle;
            artist = nextArtist == null || nextArtist.trim().isEmpty() ? source : nextArtist;
            Bitmap art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            artwork = art;
            if(art!=null)artworkCache.put(trackKey(title,artist),art);
            updateQueue(active);
        } catch (Throwable ignored) { }
    }
    private static volatile long[] queueIds=new long[0];
    public static boolean toggleFavorite(Context context){
        MediaController active=controller;
        if(active==null){android.widget.Toast.makeText(context,"No active media session",android.widget.Toast.LENGTH_SHORT).show();return false;}
        try{
            PlaybackState state=active.getPlaybackState();
            if(state!=null){
                PlaybackState.CustomAction best=null;int bestScore=-1;
                for(PlaybackState.CustomAction action:state.getCustomActions()){
                    String key=(action.getAction()+" "+action.getName()).toLowerCase(java.util.Locale.US);
                    if(key.contains("dislike")||key.contains("thumb_down"))continue;
                    boolean add=key.contains("like")||key.contains("favorite")||key.contains("favourite")||key.contains("heart")||key.contains("save")||key.contains("thumb_up")||key.contains("library");
                    if(!add)continue;
                    boolean remove=key.contains("unlike")||key.contains("remove")||key.contains("unsave");
                    int score=(liked==remove?4:1)+(key.contains("heart")||key.contains("favorite")?2:0);
                    if(score>bestScore){best=action;bestScore=score;}
                }
                if(best!=null){active.getTransportControls().sendCustomAction(best,best.getExtras());liked=!liked;return true;}
                if((state.getActions()&PlaybackState.ACTION_SET_RATING)!=0){
                    active.getTransportControls().setRating(Rating.newHeartRating(!liked));liked=!liked;return true;
                }
            }
        }catch(Throwable ignored){ }
        android.widget.Toast.makeText(context,"This player does not expose a favorite action",android.widget.Toast.LENGTH_SHORT).show();
        return false;
    }
    public static void playQueueItem(Context context,int index){
        long[] ids=queueIds;
        if(controller!=null&&index>=0&&index<ids.length){
            try{controller.getTransportControls().skipToQueueItem(ids[index]);}catch(RuntimeException e){android.widget.Toast.makeText(context,"Player does not support queue selection",android.widget.Toast.LENGTH_SHORT).show();}
        }
    }
    private static void updateQueue(MediaController active){
        queueIds=new long[0];
        if(active==null){queueTitles=new String[0];queueArtists=new String[0];queueArtwork=new Bitmap[0];return;}
        try{
            List<android.media.session.MediaSession.QueueItem> items=active.getQueue();
            if(items==null||items.isEmpty()){queueTitles=new String[0];queueArtists=new String[0];queueArtwork=new Bitmap[0];return;}
            long currentId=active.getPlaybackState()==null?-1:active.getPlaybackState().getActiveQueueItemId();
            java.util.ArrayList<String> titles=new java.util.ArrayList<>(),artists=new java.util.ArrayList<>();
            java.util.ArrayList<Bitmap> images=new java.util.ArrayList<>();
            java.util.ArrayList<Long> ids=new java.util.ArrayList<>();
            for(android.media.session.MediaSession.QueueItem item:items){
                if(item==null||item.getQueueId()==currentId)continue;
                android.media.MediaDescription d=item.getDescription();
                CharSequence t=d==null?null:d.getTitle(),a=d==null?null:d.getSubtitle();
                String track=t==null?"UPCOMING TRACK":t.toString(),performer=a==null?"":a.toString();
                boolean sameAsCurrent=track.trim().equalsIgnoreCase(title.trim())&&
                    (artist.trim().isEmpty()||performer.trim().isEmpty()||performer.trim().equalsIgnoreCase(artist.trim()));
                if(sameAsCurrent)continue;
                titles.add(track);artists.add(performer);
                ids.add(item.getQueueId());
                Bitmap image=descriptionArtwork(d);
                if(image==null)image=artworkCache.get(trackKey(track,performer));
                if(image==null){String remote=descriptionArtworkUrl(d);if(remote!=null)requestRemoteArtwork(track,performer,remote);}
                // Many automotive media sessions omit queue-item bitmaps even when all
                // queued tracks belong to the current artist/album. Reuse the live
                // session artwork only for that matching artist instead of showing a
                // misleading generic glyph.
                if(image==null&&artwork!=null&&!artist.trim().isEmpty()&&
                    (performer.trim().isEmpty()||performer.trim().equalsIgnoreCase(artist.trim())))image=artwork;
                images.add(image);if(titles.size()>=3)break;
            }
            queueTitles=titles.toArray(new String[0]);queueArtists=artists.toArray(new String[0]);queueArtwork=images.toArray(new Bitmap[0]);
            long[] result=new long[ids.size()];for(int i=0;i<result.length;i++)result[i]=ids.get(i);queueIds=result;
        }catch(Throwable ignored){queueTitles=new String[0];queueArtists=new String[0];queueArtwork=new Bitmap[0];}
    }

    private static Bitmap descriptionArtwork(android.media.MediaDescription description){
        if(description==null)return null;
        Bitmap result=description.getIconBitmap();if(result!=null)return result;
        result=loadLocalArtwork(description.getIconUri());if(result!=null)return result;
        try{
            android.os.Bundle extras=description.getExtras();
            if(extras!=null)for(String key:extras.keySet()){
                String lower=key==null?"":key.toLowerCase(java.util.Locale.US);
                if(!(lower.contains("art")||lower.contains("album")||lower.contains("icon")||lower.contains("image")||lower.contains("thumb")))continue;
                Object value;try{value=extras.get(key);}catch(Throwable ignored){continue;}
                if(value instanceof Bitmap)return (Bitmap)value;
                if(value instanceof android.net.Uri){result=loadLocalArtwork((android.net.Uri)value);if(result!=null)return result;}
                if(value instanceof String){try{result=loadLocalArtwork(android.net.Uri.parse((String)value));if(result!=null)return result;}catch(Throwable ignored){}}
            }
        }catch(Throwable ignored){}
        return null;
    }

    private static Bitmap loadLocalArtwork(android.net.Uri uri){
        if(uri==null||instance==null)return null;
        String scheme=uri.getScheme();
        if(scheme==null||(!scheme.equals("content")&&!scheme.equals("file")&&!scheme.equals("android.resource")))return null;
        java.io.InputStream stream=null;
        try{stream=instance.getContentResolver().openInputStream(uri);return android.graphics.BitmapFactory.decodeStream(stream);}
        catch(Throwable ignored){return null;}finally{try{if(stream!=null)stream.close();}catch(Throwable ignored){}}
    }

    private static String descriptionArtworkUrl(android.media.MediaDescription description){
        if(description==null)return null;
        android.net.Uri icon=description.getIconUri();
        if(icon!=null&&(icon.getScheme()!=null)&&(icon.getScheme().equals("http")||icon.getScheme().equals("https")))return icon.toString();
        try{
            android.os.Bundle extras=description.getExtras();
            if(extras!=null)for(String key:extras.keySet()){
                String lower=key==null?"":key.toLowerCase(java.util.Locale.US);
                if(!(lower.contains("art")||lower.contains("album")||lower.contains("icon")||lower.contains("image")||lower.contains("thumb")))continue;
                Object value;try{value=extras.get(key);}catch(Throwable ignored){continue;}
                String candidate=value instanceof android.net.Uri?value.toString():value instanceof String?(String)value:null;
                if(candidate!=null&&(candidate.startsWith("https://")||candidate.startsWith("http://")))return candidate;
            }
        }catch(Throwable ignored){}
        return null;
    }

    private static void requestRemoteArtwork(String track,String performer,String address){
        final String key=trackKey(track,performer);
        if(artworkCache.containsKey(key)||!artworkRequests.add(key))return;
        artworkExecutor.execute(() -> {
            java.net.HttpURLConnection connection=null;java.io.InputStream stream=null;
            try{
                connection=(java.net.HttpURLConnection)new java.net.URL(address).openConnection();
                connection.setConnectTimeout(5000);connection.setReadTimeout(7000);connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent","TRX-APEX/5.6 Android");
                stream=connection.getInputStream();Bitmap bitmap=android.graphics.BitmapFactory.decodeStream(stream);
                if(bitmap!=null)artworkCache.put(key,bitmap);
            }catch(Throwable ignored){}
            finally{artworkRequests.remove(key);try{if(stream!=null)stream.close();}catch(Throwable ignored){}if(connection!=null)connection.disconnect();}
        });
    }

    private static String trackKey(String track,String performer){
        return ((track==null?"":track)+"|"+(performer==null?"":performer)).trim().toLowerCase(java.util.Locale.US);
    }

    public static void clearArtworkCache(){artworkCache.clear();queueArtwork=new Bitmap[queueTitles.length];}

    public static long currentPositionMs(){
        long result=positionMs;
        if(playing)result+=Math.max(0,android.os.SystemClock.elapsedRealtime()-positionCapturedAt);
        if(durationMs>0)result=Math.min(result,durationMs);
        return Math.max(0,result);
    }

    public static void seekTo(Context context,long position){try{refresh(context);if(controller!=null)controller.getTransportControls().seekTo(Math.max(0,position));}catch(Throwable ignored){}}

    public static void previous(Context context) {
        try { refresh(context); if (controller != null) controller.getTransportControls().skipToPrevious(); }
        catch (Throwable ignored) { }
    }

    public static void next(Context context) {
        try { refresh(context); if (controller != null) controller.getTransportControls().skipToNext(); }
        catch (Throwable ignored) { }
    }

    public static void toggle(Context context) {
        if (!hasAccess(context)) {
            requestAccess(context);
            return;
        }
        try {
            refresh(context);
            if (controller == null) return;
            if (playing) controller.getTransportControls().pause();
            else controller.getTransportControls().play();
        } catch (Throwable ignored) { }
    }
}
