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
    private static volatile String ratingTrack="";
    private static volatile float playbackSpeed=1f;
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
        detachController(); updateMetadata(null);
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

    public static synchronized void refresh(Context context) {
        if (!hasAccess(context)) {
            detachController(); updateMetadata(null);
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

    private static synchronized void attachController(MediaController next) {
        if (controller == next || (controller != null && next != null && controller.getSessionToken().equals(next.getSessionToken()))) return;
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
                if(controller!=next)return;
                detachController();updateMetadata(null);
                title = "NO TRACK SELECTED";
                artist = "Choose a media app";
                artwork = null;
                playing = false;
            }
        };
        try { next.registerCallback(callback,new android.os.Handler(android.os.Looper.getMainLooper())); } catch (Throwable ignored) { }
    }

    private static synchronized void detachController() {
        try { if (controller != null && callback != null) controller.unregisterCallback(callback); }
        catch (Throwable ignored) { }
        controller = null;
        callback = null;
    }

    private static synchronized void updateMetadata(MediaController active) {
        if (active == null) {
            title = "NO ACTIVE MEDIA SESSION";
            artist = "Start music, then return here";
            source = "";
            liked=false;ratingTrack="";durationMs=0;positionMs=0;queueIds=new long[0];
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
            positionCapturedAt = state != null && state.getLastPositionUpdateTime()>0 ? state.getLastPositionUpdateTime() : android.os.SystemClock.elapsedRealtime();
            playbackSpeed=state==null?1f:state.getPlaybackSpeed();
            MediaMetadata metadata = active.getMetadata();
            if (metadata == null) {
                liked=false;durationMs=0;updateQueue(null);
                title = "WAITING FOR TRACK INFO";
                artist = source;
                artwork = null;
                return;
            }
            durationMs = Math.max(0,metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
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
            String identity=source+"|"+metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)+"|"+title+"|"+artist;
            if(!identity.equals(ratingTrack)){liked=false;ratingTrack=identity;}
            try {
                Rating rating=metadata.getRating(MediaMetadata.METADATA_KEY_USER_RATING);
                if(rating!=null&&rating.isRated()) {
                    if(rating.getRatingStyle()==Rating.RATING_HEART)liked=rating.hasHeart();
                    else if(rating.getRatingStyle()==Rating.RATING_THUMB_UP_DOWN)liked=rating.isThumbUp();
                } else if(state!=null) {
                    // A remove-like action is authoritative evidence of the current state.
                    boolean add=false,remove=false;
                    for(PlaybackState.CustomAction a:state.getCustomActions()) {
                        String key=favoriteKey(a);if(!isFavoriteAction(key))continue;
                        if(isRemoveFavorite(key))remove=true;else add=true;
                    }
                    if(remove&&!add)liked=true;else if(add&&!remove)liked=false;
                }
            } catch(RuntimeException ignored) {}
            Bitmap art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            artwork = art;
            if(art!=null)artworkCache.put(trackKey(title,artist),art);
            updateQueue(active);
        } catch (Throwable ignored) { }
    }
    private static volatile long[] queueIds=new long[0];
    public static boolean hasSession(){return controller!=null;}
    public static boolean supports(long action){
        MediaController c=controller;PlaybackState state=c==null?null:c.getPlaybackState();
        return state!=null && (state.getActions()&action)!=0;
    }
    private static String favoriteKey(PlaybackState.CustomAction action){
        return (action.getAction()+" "+action.getName()).toLowerCase(java.util.Locale.US);
    }
    private static boolean isRemoveFavorite(String key){return key.contains("unlike")||key.contains("unfavorite")||key.contains("unfavourite")||key.contains("remove");}
    private static boolean isFavoriteAction(String key){
        if(key.contains("dislike")||key.contains("thumb_down")||key.contains("thumbs_down"))return false;
        return key.contains("like")||key.contains("favorite")||key.contains("favourite")||key.contains("heart")||key.contains("thumb_up")||key.contains("thumbs_up");
    }
    private static PlaybackState.CustomAction favoriteAction(MediaController active){
        PlaybackState state=active.getPlaybackState();if(state==null)return null;
        PlaybackState.CustomAction best=null;int score=-1;
        for(PlaybackState.CustomAction action:state.getCustomActions()){
            String key=favoriteKey(action);if(!isFavoriteAction(key))continue;
            int next=(liked==isRemoveFavorite(key)?4:1);
            if(next>score){best=action;score=next;}
        }
        return best;
    }
    public static boolean canFavorite(){
        MediaController c=controller;if(c==null)return false;
        int type=c.getRatingType();
        return favoriteAction(c)!=null || (supports(PlaybackState.ACTION_SET_RATING) && (type==Rating.RATING_HEART||type==Rating.RATING_THUMB_UP_DOWN));
    }
    public static boolean toggleFavorite(Context context){
        refresh(context);MediaController active=controller;if(active==null)return false;
        try {
            PlaybackState.CustomAction action=favoriteAction(active);
            if(action!=null){active.getTransportControls().sendCustomAction(action,action.getExtras());return true;}
            if(!supports(PlaybackState.ACTION_SET_RATING))return false;
            int type=active.getRatingType();
            Rating rating=type==Rating.RATING_HEART?Rating.newHeartRating(!liked):type==Rating.RATING_THUMB_UP_DOWN?(liked?Rating.newUnratedRating(Rating.RATING_THUMB_UP_DOWN):Rating.newThumbRating(true)):null;
            if(rating==null)return false;
            active.getTransportControls().setRating(rating);
            // Do not fake an acknowledgement: metadata/custom actions update the heart.
            return true;
        }catch(RuntimeException ignored){return false;}
    }
    public static final class QueueSnapshot {
        public final String[] titles,artists; public final Bitmap[] artwork; public final long[] ids;
        QueueSnapshot(){titles=queueTitles.clone();artists=queueArtists.clone();artwork=queueArtwork.clone();ids=queueIds.clone();}
    }
    public static synchronized QueueSnapshot queueSnapshot(){return new QueueSnapshot();}
    public static String queueIdAt(int index){long[] ids=queueIds;return index>=0&&index<ids.length?Long.toString(ids[index]):"";}
    public static boolean playQueueItem(Context context,String requestedId){
        refresh(context);if(!supports(PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM)||requestedId==null)return false;
        try{
            long id=Long.parseLong(requestedId);boolean found=false;
            for(long candidate:queueIds)if(candidate==id)found=true;
            if(!found)return false;
            controller.getTransportControls().skipToQueueItem(id);return true;
        }catch(RuntimeException e){return false;}
    }
    private static synchronized void updateQueue(MediaController active){
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

    public static synchronized void clearArtworkCache(){artworkCache.clear();queueArtwork=new Bitmap[queueTitles.length];}

    public static long currentPositionMs(){
        long result=positionMs;
        if(playing)result+=(long)(Math.max(0,android.os.SystemClock.elapsedRealtime()-positionCapturedAt)*playbackSpeed);
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
