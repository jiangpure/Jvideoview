package com.jplus.jvideoview.jvideo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.widget.LinearLayout
import com.jplus.jvideoview.JvController
import com.jplus.jvideoview.data.Video
import com.jplus.jvideoview.utils.JvUtil
import com.jplus.jvideoview.utils.JvUtil.dt2progress
import tv.danmaku.ijk.media.player.AndroidMediaPlayer
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.IOException
import kotlin.math.floor


/**
 * @author JPlus
 * @date 2019/8/30.
 */
class JvPresenter(
    //所在的上下文
    private val mActivity: Activity,
    //播放器view
    private val mView: JvView,
    //默认params
    private val mDefaultParams: ViewGroup.LayoutParams,
    //播放器状态回调
    private val mCallback: JvController.JvCallBack,
    //默认播放引擎
    private var mPlayer: IMediaPlayer
) :
    JvContract.Presenter {
    private var mSurface: Surface? = null
    private var mTextureView: TextureView? = null
    private var mAudioManager: AudioManager? = null
    private var mRunnable: Runnable? = null

    private var mIsBackContinue: Boolean? = null

    private var mPlayState = PlayState.STATE_IDLE
    private var mPlayMode = PlayMode.MODE_NORMAL
    //初始音量
    private var mDefaultVolume = 0

    private var mStartPosition = 0
    //初始亮度
    private var mStartLight = 0
    //播放进度
    private var mPosition = 0
    //调节的音量
    private var mVolume = 0
    //调节的亮度
    private var mLight = 0
    private var mBufferPercent = 0
    //    private var mVideoIndex = 0
    private var mLoadingNums= mutableSetOf<Int>()
    private var mAdjustWay = -1

    //是否第一次按下，用于滑动判断
    private var mIsFirstDown = true
    //中间的控制view是否显示中
    private var mCenterControlViewIsShow = false
    //是否加载中
    private var mIsLoading = false
    //是否静音
    private var mIsVolumeMute = false
    //是否强制翻转屏幕
    private var mIsForceScreen = false

    private var mJvCallBack: VideoPlayCallBack? = null

    private val mHandler by lazy {
        Handler()
    }
    private var mVideo: Video? = null

    private val mHideRunnable: Runnable by lazy {
        //延时后执行
        Runnable {
            mView.hideController()
            Log.d(JvCommon.TAG, " mView.hideController()")
            mCenterControlViewIsShow = false
        }
    }

    init {
        mView.setPresenter(this)
        //保存普通状态下的布局参数
        Log.d(JvCommon.TAG, "orientation:" + mActivity.requestedOrientation)
    }


    override fun isSupportPlayEngine(isSupport: Boolean) {
        mView.showSwitchEngine(isSupport)
    }

    override fun switchPlayEngine(playerEngine: Int) {
        mPlayer = when (playerEngine) {
            //使用ijkplayer播放引擎
            PlayBackEngine.PLAYBACK_IJK_PLAYER -> IjkMediaPlayer()
            //使用android自带的播放引擎
            PlayBackEngine.PLAYBACK_MEDIA_PLAYER -> AndroidMediaPlayer()
            //使用exoplayer引擎
//            PlayBackEngine.PLAYBACK_EXO_PLAYER ->Exo
            else -> AndroidMediaPlayer()
        }
        reStartPlay()
    }

    override fun subscribe() {
        showLoading( "音频初始化中...", 1)
        initAudio()
        closeLoading("音频初始化完成", 1)
    }

    override fun unSubscribe() {

    }

    //初始化Media和volume
    private fun initAudio() {
        mAudioManager = mActivity.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //8.0以上需要响应音频焦点的状态改变
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            /*
            AUDIOFOCUS_GAIN  的使用场景：应用需要聚焦音频的时长会根据用户的使用时长改变，属于不确定期限。例如：多媒体播放或者播客等应用。
            AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK  的使用场景：应用只需短暂的音频聚焦，来播放一些提示类语音消息，或录制一段语音。例如：闹铃，导航等应用。
            AUDIOFOCUS_GAIN_TRANSIENT  的使用场景：应用只需短暂的音频聚焦，但包含了不同响应情况，例如：电话、QQ、微信等通话应用。
            AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE  的使用场景：同样您的应用只是需要短暂的音频聚焦。未知时长，但不允许被其它应用截取音频焦点。例如：录音软件。
            */
            val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener {
                } // Need to implement listener
                .build()
            mAudioManager?.requestAudioFocus(audioFocusRequest)
        } else {
            mAudioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        //初始音量值
        mDefaultVolume = getVolume(false)
        //初始亮度值
        mStartLight = getLight(false)
    }

    //初始化播放器监听
    private fun initPlayerListener() {
        mPlayer.let {
            //设置是否循环播放，默认可不写
            it.isLooping = false
            //设置播放类型
            if (mPlayer is AndroidMediaPlayer) {
                Log.d(JvCommon.TAG, "AndroidMediaPlayer")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val attributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build()
                    (it as AndroidMediaPlayer).internalMediaPlayer.setAudioAttributes(attributes)
                } else {
                    it.setAudioStreamType(AudioManager.STREAM_MUSIC)
                }
            } else {
                it.setAudioStreamType(AudioManager.STREAM_MUSIC)
            }

            //播放完成监听
            it.setOnCompletionListener {
                completedPlay(null)
                mCallback.endPlay()
            }

            //seekTo()调用并实际查找完成之后
            it.setOnSeekCompleteListener {
                // mPlayState = PlayState.STATE_IDLE
                Log.d(JvCommon.TAG, "setOnSeekCompleteListener")
                seekCompleted(it.currentPosition)
            }

            //预加载监听
            it.setOnPreparedListener {
                preparedPlay()
            }

            //相当于缓存进度条
            it.setOnBufferingUpdateListener { _, percent ->
                buffering(percent)
            }

            //播放错误监听
            it.setOnErrorListener { _, what, extra ->
                errorPlay(what, extra, "播放错误，请重试~")
                true
            }

            //播放信息监听
            it.setOnInfoListener { _, what, _ ->
                when (what) {
                    MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                        // 播放器开始渲染
                        mPlayState = PlayState.STATE_PLAYING
                        Log.d(JvCommon.TAG, "MEDIA_INFO_VIDEO_RENDERING_START")
                    }
                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                        bufferStart()

                    }
                    IMediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                        bufferEnd()
                    }
                    MediaPlayer.MEDIA_INFO_NOT_SEEKABLE -> {
                        //无法seekTo
                        notSeek()
                    }
                }
                true
            }
            //播放尺寸
            it.setOnVideoSizeChangedListener { _, _, _, _, _ ->
                //这里是视频的原始尺寸大小
                Log.d(JvCommon.TAG, "setOnVideoSizeChangedListener")
                mTextureView?.layoutParams = JvUtil.changeVideoSize(mView.width, mView.height, it.videoWidth, it.videoHeight)
            }
            //设置Option
            if (it is IjkMediaPlayer) {
                it.setOption(
                    IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                    "start-on-prepared",
                    0
                )
                it.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 1)
                it.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 0)
            }

            //设置是否保持屏幕常亮
            it.setScreenOnWhilePlaying(true)
            if (it is IjkMediaPlayer) {
                it.setOnNativeInvokeListener(IjkMediaPlayer.OnNativeInvokeListener { _, _ ->
                    true
                })
                it.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "reconnect", 5)
                it.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1)
            }
        }
    }

    //视频播放准备
    private fun loadVideo(surface: Surface, video: Video) {
        Log.d(JvCommon.TAG, "entryVideo:${video}")

        showLoading( "视频预加载...", 4)

        //设置title
        mView.setTitle(video.videoName ?: "未知视频")
        mPlayer.let {

            //如果不是IDLE状态就改变播放器状态
            if (mPlayState != PlayState.STATE_IDLE) {
                resetPlay()
            }
            try {
                val url = video.videoUrl
                it.dataSource = (url)
                //加载url之后为播放器初始化完成状态
                mPlayState = PlayState.STATE_INITLIZED
                //设置渲染画板
                it.setSurface(surface)
                //初始化播放器监听
                initPlayerListener()
                //异步的方式装载流媒体文件
                it.prepareAsync()
            } catch (e: IOException) {
                e.printStackTrace()
                errorPlay(0, 0, "视频路径有误或者地址失效~")
            }
        }
    }

    override fun controlPlay() {
        when (mPlayState) {
            PlayState.STATE_PLAYING, PlayState.STATE_BUFFERING_PLAYING -> pausePlay()
            PlayState.STATE_PAUSED, PlayState.STATE_BUFFERING_PAUSED -> continuePlay()
            PlayState.STATE_PREPARED -> startPlay()
        }
    }

    //开始播放
    override fun startPlay(position: Int) {
        if (mPlayState == PlayState.STATE_PREPARED) {
            Log.d(JvCommon.TAG, "startPlay:$position")
            mPlayer.let {
                //如果不在播放中，指定视频播放位置并开始播放
                if(position!=0) soughtTo(position.toLong())
                it.start()
                mPlayState = PlayState.STATE_PLAYING
            }
            mView.startVideo(position)
            runVideoTime()
            mCallback.startPlay()
        }
    }

    override fun continuePlay() {
        Log.d(JvCommon.TAG, "continuePlay")
        when (mPlayState) {
            PlayState.STATE_PAUSED, PlayState.STATE_COMPLETED -> {
                mPlayer.start()
                mPlayState = PlayState.STATE_PLAYING
            }
            PlayState.STATE_BUFFERING_PAUSED -> {
                mPlayer.start()
                mPlayState = PlayState.STATE_BUFFERING_PLAYING
            }
            PlayState.STATE_ERROR -> {
                reStartPlay()
            }
        }
        mView.continueVideo()
        runVideoTime()
    }

    private fun bufferStart() {
        //loading
        showLoading("缓冲中....", 3)

        Log.d(JvCommon.TAG, "bufferStart:state$mPlayState")
        // MediaPlayer暂时不播放，以缓冲更多的数据
        mPlayState =
            if (mPlayState == PlayState.STATE_PAUSED) {
                PlayState.STATE_BUFFERING_PAUSED
            } else if (mPlayState == PlayState.STATE_PLAYING) {
                PlayState.STATE_BUFFERING_PLAYING
            } else {
                return
            }
    }

    private fun seekCompleted(position: Long) {
        Log.d(JvCommon.TAG, "seekCompleted")
        closeLoading("seek完成", 5)
    }

    private fun buffering(percent: Int) {
//        Log.d(JvCommon.TAG, "buffering$percent")
        if (percent != 0) {
            mBufferPercent = percent
        }
        mView.showBuffering(percent)
        mPlayer.let {
            if (it is IjkMediaPlayer) {
                mView.showNetSpeed("" + it.tcpSpeed / 1024 + "K/s")//缓冲时显示网速
            }
        }
    }

    private fun bufferEnd() {
        Log.d(JvCommon.TAG, "buffered")
        // 填充缓冲区后，MediaPlayer恢复播放/暂停
        if (mPlayState == PlayState.STATE_BUFFERING_PLAYING) {
            continuePlay()
        } else if (mPlayState == PlayState.STATE_BUFFERING_PAUSED) {
            pausePlay()
        }
        closeLoading("缓冲完成", 3)
    }

    override fun seekCompletePlay(position: Int) {
        Log.d(JvCommon.TAG, "seekToPlay:$position")
        mPlayer.let {
            if (mPlayState == PlayState.STATE_PAUSED || mPlayState == PlayState.STATE_BUFFERING_PAUSED) {
                soughtTo(position.toLong())
                pausePlay()
            } else if (mPlayState == PlayState.STATE_PLAYING || mPlayState == PlayState.STATE_BUFFERING_PLAYING) {
                soughtTo(position.toLong())
                continuePlay()
            } else if (mPlayState == PlayState.STATE_PREPARED) {
                startPlay(position)
            }
        }
        mPosition = position
    }

    override fun seekingPlay(position: Int, isSlide: Boolean) {
        mView.seekingVideo(getVideoTimeStr(position), position, isSlide)
    }

    private fun notSeek() {

    }

    private fun soughtTo(position: Long) {
        //loading
        showLoading("seek中....", 5)
        mPlayer.seekTo(position)
    }

    //暂停播放
    override fun pausePlay() {
        Log.d(JvCommon.TAG, "pausePlay")
        mPlayState =
            if (mPlayState == PlayState.STATE_PLAYING || mPlayState == PlayState.STATE_PREPARED) {
                PlayState.STATE_PAUSED
            } else if (mPlayState == PlayState.STATE_BUFFERING_PLAYING) {
                PlayState.STATE_BUFFERING_PAUSED
            } else {
                return
            }
        mPlayer.pause()
        stopVideoTime()
        stopHideControlUi()
        showControlUi(false)
        mView.pauseVideo()
    }

    fun setMessagePrompt(message: String) {
        mView.closeCenterControlView()
        mView.closeLoading("所有的loading")
        mView.showMessagePrompt(message)
    }

    override fun completedPlay(videoUrl: String?) {
        //播放完成状态
        mPlayState = PlayState.STATE_COMPLETED
        Log.d(JvCommon.TAG, "completedPlay")
        mCallback.endPlay()
        mJvCallBack?.videoCompleted()
    }

    override fun preparedPlay() {
        //预加载完成状态
        mPlayState = PlayState.STATE_PREPARED

        closeLoading("预加载完成", 4)
        Log.d(JvCommon.TAG, "setOnPreparedListener")
        mPlayer.let {
            //预加载后先播放再暂停，1：防止播放错误-38(未开始就停止) 2：可以显示第一帧画面
            mView.preparedVideo(getVideoTimeStr(null), it.duration.toInt())
        }
        showControlUi(false)
    }

    override fun resetPlay() {
        mPlayer.reset()
        mView.reset()
        mPlayState = PlayState.STATE_IDLE
    }

    override fun reStartPlay() {
        resetPlay()
        mVideo?.let {
            mJvCallBack?.let { call ->
                startVideo(it, call)
            }
        }
    }

    override fun errorPlay(what: Int, extra: Int, message: String) {
        Log.d(JvCommon.TAG, "setOnErrorListener:$what,$message")
        mPlayState = PlayState.STATE_ERROR
        mView.showMessagePrompt(message)
    }

    override fun onPause() {
        //取消屏幕常亮
        mActivity.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mIsBackContinue =
            if (mPlayState == PlayState.STATE_PAUSED || mPlayState == PlayState.STATE_BUFFERING_PAUSED) {
                false
            } else if (mPlayState == PlayState.STATE_PLAYING || mPlayState == PlayState.STATE_BUFFERING_PLAYING) {
                true
            } else {
                //播放器初始化前、初始化中、初始化后或者播放完成、播放错误时中不做任何操作
                mIsBackContinue = null
                return
            }
        pausePlay()
    }

    override fun onResume() {
        //设置屏幕常亮
        mActivity.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //播放器初始化中不做任何操作
        mIsBackContinue?.let {
            if (it) continuePlay() else pausePlay()
        }
    }

    //====================================================================================
    // 1.创建一个手势监听回调
    private val listener: SimpleOnGestureListener = object : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            Log.d(JvCommon.TAG, "onDown")
            return super.onDown(e)
        }

        override fun onShowPress(e: MotionEvent) {
            Log.d(JvCommon.TAG, "onShowPress")
            super.onShowPress(e)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            Log.d(JvCommon.TAG, "onDoubleTap")
            mPlayer.let {
                if (mPlayState == PlayState.STATE_PLAYING || mPlayState == PlayState.STATE_BUFFERING_PLAYING) {
                    pausePlay()
                } else if (mPlayState == PlayState.STATE_BUFFERING_PAUSED || mPlayState == PlayState.STATE_PAUSED) {
                    continuePlay()
                } else if (mPlayState == PlayState.STATE_PREPARED) {
                    startPlay()
                }
            }
            return super.onDoubleTap(e)
        }

        override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
            Log.d(JvCommon.TAG, "onSingleTapConfirmed")
            if (mCenterControlViewIsShow) hideControlUi() else showControlUi(true)
            return true
        }

        override fun onScroll(
            e1: MotionEvent,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (mIsFirstDown) {
                mIsFirstDown = false
                mAdjustWay = getAdjustMode(e1)
            }
            //水平滑动的距离
            val distX = e2.x - e1.x
            //竖直滑动的距离
            val distY = e2.y - e1.y
            //从手指落下时判断滑动时改变的模式
            when (mAdjustWay) {
                PlayAdjust.ADJUST_VOLUME -> {
                    //音量调节，从下往上为加，所以需要加上负号
                    if (!mIsVolumeMute) {
                        setVolume(mDefaultVolume, -distY)
                    }
                }
                PlayAdjust.ADJUST_LIGHT -> {
                    // 亮度调节
                    setLight(mStartLight, -distY)
                }
                PlayAdjust.ADJUST_VIDEO -> {
                    //快进/后退
                    slidePlay(mStartPosition, distX)
                }
            }
            return super.onScroll(e1, e2, distanceX, distanceY)
        }
    }
    // 2.创建一个检测器
    private val detector = GestureDetector(mActivity, listener)
    //====================================================================================

    override fun slideJudge(view: View, event: MotionEvent) {
        detector.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                endAdjust()
                mView.hideAdjustUi()
            }
        }
    }

    private fun getAdjustMode(event: MotionEvent): Int {
        //调整前获取调整的模式
        Log.d(JvCommon.TAG, "getAdjustMode")
        val width = mView.width
        return when {
            //通过起始点坐标判断滑动是 快进/后退、亮度调节、音量调节
            event.x >= 0.8 * width -> {
                PlayAdjust.ADJUST_VOLUME
            }
            event.x <= 0.2 * width -> {
                PlayAdjust.ADJUST_LIGHT
            }
            else -> {
                mStartPosition = getPosition()
                stopVideoTime()
                PlayAdjust.ADJUST_VIDEO
            }
        }
    }

    override fun setPlayForm(playForm: Int) {
        Log.d(JvCommon.TAG, "setPlayForm:$playForm")
//        mPlayForm = playForm
    }

    override fun setVolumeMute(isMute: Boolean) {
        //设置静音和恢复静音前音量
        mAudioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, if (isMute) 0 else mVolume, 0)
        mIsVolumeMute = isMute
    }

    private fun endAdjust() {
        //调整结束后保存结果
        Log.d(JvCommon.TAG, "endAdjust")
        mIsFirstDown = true
        when (mAdjustWay) {
            PlayAdjust.ADJUST_LIGHT -> {
                //保存亮度
                mStartLight = mLight
            }
            PlayAdjust.ADJUST_VOLUME -> {
                //保存音量
                mDefaultVolume = mVolume
            }
            PlayAdjust.ADJUST_VIDEO -> {
                //保存并跳到指定位置播放
                mStartPosition = mPosition
                seekCompletePlay(mStartPosition)
            }
        }
        mAdjustWay = -1
    }

    override fun onConfigChanged(newConfig: Configuration) {
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            entryPortraitScreen()
            Log.d(JvCommon.TAG, "Configuration.ORIENTATION_PORTRAIT")
        }
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            entryFullScreen()
            Log.d(JvCommon.TAG, "Configuration.ORIENTATION_LANDSCAPE")
        }
    }

    override fun switchSpecialMode(switchMode: Int, isRotateScreen: Boolean) {
        Log.d(JvCommon.TAG, "playMode$mPlayMode")
        when (mPlayMode) {
            PlayMode.MODE_NORMAL -> {
                if (switchMode == SwitchMode.SWITCH_TO_FULL) {
                    //进入全屏模式（在dialog的模式下似乎会有适配问题）
                    mPlayMode = PlayMode.MODE_FULL_SCREEN
                    //屏幕旋转时指定带重力感应的屏幕方向不然会转不过来...，但是没有开启旋转的情况下要强制转屏来达到全屏效果
                    mActivity.requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    //屏幕方向改为未知，保证下次能够旋转屏幕
//                    mActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            PlayMode.MODE_FULL_SCREEN -> {
                exitMode(true, isRotateScreen)
            }
        }
    }

    private fun entryFullScreen() {
        // 隐藏ActionBar、状态栏
        mActivity.actionBar?.hide()

        mActivity.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        //设置为充满父布局
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        //隐藏虚拟按键，并且全屏
        mActivity.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
        mView.layoutParams = params

        //全屏直接使用手机大小,此时未翻转的话，高宽对调
        val phoneWidth = JvUtil.getPhoneDisplayWidth(mActivity)
        val phoneHeight = JvUtil.getPhoneDisplayHeight(mActivity)
        mTextureView?.layoutParams = JvUtil.changeVideoSize(
            if (phoneHeight > phoneWidth) phoneHeight else phoneWidth,
            if (phoneHeight > phoneWidth) phoneWidth else phoneHeight,
            mPlayer.videoWidth,
            mPlayer.videoHeight
        )
        mView.entryFullMode()
    }

    private fun entryPortraitScreen() {
        //进入普通模式
        mDefaultParams.let {
            mPlayMode = PlayMode.MODE_NORMAL
            mActivity.actionBar?.show()
            mActivity.window.clearFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            mActivity.window.decorView.systemUiVisibility = View.VISIBLE
            mView.layoutParams = it
            mTextureView?.layoutParams = JvUtil.changeVideoSize(it.width, it.height, mPlayer.videoWidth, mPlayer.videoHeight)
        }
        //屏幕方向改为未知，保证下次能够旋转屏幕
//        mActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        mView.exitMode()
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun exitMode(isBackNormal: Boolean, isRotateScreen: Boolean) {
        Log.d(JvCommon.TAG, "exitMode")
        if (getPlayMode() != PlayMode.MODE_NORMAL && isBackNormal) {
            mActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    //播放进度开始计时
    private fun runVideoTime() {
        Log.d(JvCommon.TAG, "runVideoTime")
        mRunnable = mRunnable ?: Runnable {
            mPlayer.let {
                if (it.isPlaying) {
                    //更新播放进度
                    mView.playing(
                        getVideoTimeStr(it.currentPosition.toInt()),
                        it.currentPosition.toInt()
                    )
                }
            }
            //重复调起自身
            mHandler.postDelayed(mRunnable, 200)
        }
        mHandler.post(mRunnable)
        runHideControlUi(5000)
    }

    //播放进度停止计时
    private fun stopVideoTime() {
        mHandler.removeCallbacks(mRunnable)
    }

    private fun runHideControlUi(time:Long){
        Log.d(JvCommon.TAG, "runHideControlUi")
        if (mPlayState == PlayState.STATE_PLAYING || mPlayState == PlayState.STATE_BUFFERING_PLAYING) {
            mHandler.postDelayed(mHideRunnable, time)
        }
    }
    private fun showControlUi(autoHide:Boolean){
        mCenterControlViewIsShow =true
        if(!mIsLoading){ //不在加载中则显示中心按钮
            mView.showCenterControlView()
        }
        mView.showController()
        if(autoHide){
            runHideControlUi(5000)
        }
    }
    private fun hideControlUi(){
        stopHideControlUi() // 去掉自动隐藏
        mCenterControlViewIsShow = false
        mView.hideController()
    }
    private fun stopHideControlUi(){
        mHandler.removeCallbacks(mHideRunnable)
    }

    private fun showLoading(content: String, loadingNum: Int){
        if(loadingNum in mLoadingNums){
            Log.e(JvCommon.TAG, "loading- show[$loadingNum] is exist.")
        }else{
            mLoadingNums.add(loadingNum)
            mView.showLoading(content)
            mIsLoading =true
            Log.d(JvCommon.TAG, "loading-show[$loadingNum]")
        }
    }

    private fun closeLoading(content: String, loadingNum: Int){
        if(loadingNum in mLoadingNums){
            mLoadingNums.remove(loadingNum)
            mView.closeLoading(content)
            mIsLoading = false
            Log.d(JvCommon.TAG, "loading-close[$loadingNum]")
        }else{
            Log.e(JvCommon.TAG, "loading- close[$loadingNum] is not exist.")
        }
    }

    override fun textureReady(surface: SurfaceTexture, textureView: TextureView) {
        Log.d(JvCommon.TAG, "textureReady")
        if (mSurface == null) {
            mSurface = Surface(surface)
        }
        mTextureView = mTextureView ?: textureView
        mPlayState = PlayState.STATE_PREPARING
        mCallback.initSuccess()
    }

    fun startVideo(video: Video, callback: VideoPlayCallBack) {
        mView.reset()
        mJvCallBack = callback
        //播放视频
        mSurface?.let {
            loadVideo(it, video)
        }
    }

    override fun getPlayState(): Int {
        return mPlayState
    }

    override fun getPlayMode(): Int {
        return mPlayMode
    }

    override fun getLight(isMax: Boolean): Int {
        var nowBrightnessValue = 0
        try {
            nowBrightnessValue =
                Settings.System.getInt(mActivity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {

        }
        return nowBrightnessValue
    }

    override fun getVolume(isMax: Boolean): Int {
        return if (isMax) {
            mAudioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        } else {
            mAudioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
        } ?: 0
    }

    override fun getDuration(): Int {
        return mPlayer.duration.toInt()
    }

    override fun getPosition(): Int {
        return mPlayer.currentPosition.toInt()
    }

    override fun getBufferPercent(): Int {
        return mBufferPercent
    }

    override fun releasePlay(destroyUi: Boolean) {
        mSurface?.release()
        mHandler.removeCallbacks(mRunnable)
        mPlayer.stop()
        mPlayer.release()//调用release()方法来释放资源，资源可能包括硬件加速组件的单态固件
        mSurface = null
    }


    /**
     * 滑动屏幕快进或者后退
     * @param distance
     */
    private fun slidePlay(startProgress: Int, distance: Float) {
        if (mPlayState==PlayState.STATE_COMPLETED||mPlayState == PlayState.STATE_IDLE || mPlayState == PlayState.STATE_INITLIZED || mPlayState == PlayState.STATE_ERROR) {
            //播放状态为初始前，初始化完成以及加载完毕和错误时不能滑动播放
            Log.d(JvCommon.TAG, "can't to slide play ,state${mPlayState}")
            return
        }
        var position =
            startProgress + floor(
                dt2progress(
                    distance,
                    getDuration(),
                    (mView as LinearLayout).width,
                    0.2
                )
            ).toInt()
        when {
            position > getDuration() -> position = getDuration()
            position < 0 -> position = 0
            else -> mPosition = position //保存进度
        }
        mView.seekingVideo(getVideoTimeStr(position), position, true)
    }

    private fun setLight(startLight: Int, distance: Float) {
        Log.d(JvCommon.TAG, "startLight:$startLight, distance$distance")
        var light = startLight + floor(
            dt2progress(
                distance,
                255,
                (mView as LinearLayout).height,
                1.0
            )
        ).toInt()
        when {
            light >= 255 -> light = 255
            light <= 0 -> light = 0
            else -> mLight = light //保存亮度
        }
        //设置当前activity的亮度
        val params = mActivity.window.attributes
        params.screenBrightness = light / 255f
        mActivity.window.attributes = params

        mView.setLightUi(floor(light / 255f * 100).toInt())
    }

    private fun setVolume(startVolume: Int, distance: Float) {
        var volume =
            startVolume + floor(
                dt2progress(
                    distance,
                    getVolume(true),
                    (mView as LinearLayout).height,
                    1.0
                )
            ).toInt()
        when {
            volume in 0..getVolume(true) -> {

            }
            volume <= 0 -> volume = 0
            else -> volume = getVolume(true)
        }
        mAudioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        mVolume = volume
        mView.setVolumeUi(volume * 100 / getVolume(true))
    }

    private fun getVideoTimeStr(position: Int?): String {
        return JvUtil.progress2Time(position) + "&" + JvUtil.progress2Time(getDuration())
    }

    interface VideoPlayCallBack {
        fun videoCompleted()
    }
}