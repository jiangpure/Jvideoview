package com.jplus.jvideoview.jvideo

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import com.jplus.jvideoview.BasePresenter
import com.jplus.jvideoview.BaseView

/**
 * @author JPlus
 * @date 2019/8/30.
 */
interface JVideoViewContract {

    interface Views :BaseView<Presenter>{
        /**
         * 设置播放标题
         * @param title 标题
         */
        fun setTitle(title:String)
        /**
         * 播放器loading
         * @param isShow 是否显示进度条
         * @param text 提示文字
         */
        fun showLoading(isShow:Boolean, text:String)
        /**
         * 缓冲中
         *@param percent 百分比
         */
        fun buffering(percent:Int)
        /**
         * 播放准备就绪
         * @param videoTime 播放时间
         * @param max 最大进度
         */
        fun preparedVideo(videoTime:String, max:Int)
        /**
         * 缩略图
         */
        fun setThumbnail(bitmap:Bitmap?)
        /**
         * 开始播放
         * @param position 可选任意位置，默认为初始位置
         */
        fun startVideo(position: Int = 0)
        /**
         * 显示实时网速
         * @param speed 实时网速
         */
        fun showSpeed(speed: String)
        /**
         * 播放中
         *  @param videoTime 播放时间
         * @param position 播放位置
         */
        fun playing(videoTime:String, position: Int)
        /**
         * seek到某个位置播放
         * @param videoTime 播放时间
         * @param position 任意位置
         */
        fun seekToVideo(videoTime:String, position: Int)
        /**
         * 手势滑动快进/后退
         * @param videoTime 播放时间
         * @param position 任意位置
         */
        fun slidePlayVideo(videoTime:String, position: Int)
        /**
         * 暂停播放
         */
        fun pauseVideo()
        /**
         * 继续播放
         */
        fun continueVideo()
        /**
         * 播放完成
         */
        fun completedVideo()
        /**
         * 播放错误
         */
        fun errorVideo(errorInfo:String)
        /**
         * 调节亮度
         * @param light 亮度
         */
        fun setLightUi(light:Int)
        /**
         * 调节音量
         * @param volumePercent 音量百分比
         */
        fun setVolumeUi(volumePercent:Int)
        /**
         * 进入特殊模式
         * @param mode 全屏/窗口
         */
        fun entrySpecialMode(mode:Int)
        /**
         * 退出当前模式，恢复普通模式
         */
        fun exitMode()
        /**
         * 弹出/隐藏控制栏
         * @param isShow 是否显示
         */
        fun hideOrShowController(isShow:Boolean)
        /**
         * 隐藏进度控制ui
         */
        fun hideAdjustUi()
    }
    interface Presenter:BasePresenter {
        /**
         * 开始播放
         * @param position 可选任意位置，默认为初始位置
         */
        fun startPlay(position: Int = 0)
        /**
         * seek到某个位置继续播放
         * @param position 任意位置
         */
        fun seekToPlay(position: Int)
        /**
         * 暂停播放
         */
        fun pausePlay()
        /**
         * 继续播放
         */
        fun continuePlay()
        /**
         * 手势判断
         * @param view View
         * @param event 手势事件
         */
        fun slideJudge(view:View, event:MotionEvent)
        /**
         * 进度条拖动快进/后退
         * @param position 进度条进度
         */
        fun seekBarPlay(position: Int)
        /**
         * 进入特殊模式
         */
        fun entrySpecialMode()
        /**
         * 退出当前模式
         * @param isBackNormal  是否恢复普通模式
         */
        fun exitMode(isBackNormal:Boolean)
        /**
         * 幕布准备就绪
         * @param surface 表面
         * @param textureView 幕布View
         */
        fun textureReady(surface: SurfaceTexture, textureView: TextureView)
        /**
         * 获取播放状态
         * @return 播放的九种状态
         */
        fun getPlayState():Int
        /**
         * 获取播放器模式
         * @return 返回 全屏/窗口/普通 模式
         */
        fun getPlayMode():Int
        /**
         * 获取音量
         * @param isMax 可选是否返回最大音量
         * @return 音量大小
         */
        fun getVolume(isMax:Boolean):Int
        /**
         * 获取亮度
         * @param isMax 可选是否返回最大亮度
         * @return 亮度大小0~255
         */
        fun getLight(isMax:Boolean):Int
        /**
         * 是否静音
         * @param isMute 是否静音
         */
        fun setVolumeMute(isMute:Boolean)
        /**
         * 循环播放
         */
        fun entryVideoLoop()
        /**
         * 生命周期onPause()
         */
        fun onPause()
        /**
         * 生命周期onResume()
         */
        fun onResume()
        /**
         * 获取视频总时长
         * @return 视频时长
         */
        fun getDuration():Int
        /**
         * 获取当前播放位置
         * @return 当前视频进度
         */
        fun getPosition():Int
        /**
         * 获取当前缓冲百分比
         * @return 1~100
         */
        fun getBufferPercent():Int
        /**
         * 释放资源
         * @param destroyUi 释放MediaPlayer资源后是否退出当前模式
         */
        fun releasePlay(destroyUi:Boolean)
    }
}