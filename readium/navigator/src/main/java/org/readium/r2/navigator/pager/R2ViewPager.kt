/*
 * Module: r2-navigator-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.navigator.pager

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import org.readium.r2.navigator.BuildConfig.DEBUG
import timber.log.Timber

internal class R2ViewPager : R2RTLViewPager {

    internal enum class PublicationType {
        EPUB,
        CBZ,
        FXL,
        WEBPUB,
        AUDIO,
        DiViNa,
    }

    internal lateinit var publicationType: PublicationType

    private var startX = 0f
    private var startY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    constructor(context: Context) : super(context) {
        initVertical()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initVertical()
    }

    private fun initVertical() {
        overScrollMode = OVER_SCROLL_NEVER
        setPageTransformer(true, VerticalPageTransformer())
    }

    private fun swapXY(ev: MotionEvent): MotionEvent {
        val width = width.toFloat()
        val height = height.toFloat()
        val newX = ev.y / height * width
        val newY = ev.x / width * height
        ev.setLocation(newX, newY)
        return ev
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (publicationType == PublicationType.EPUB) {
            if (ev.action == MotionEvent.ACTION_DOWN) {
                return false
            }
        }

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - startX)
                val dy = abs(ev.y - startY)

                // Only intercept if vertical swipe is bigger than horizontal swipe
                if (dy > dx && dy > touchSlop) {
                    // For EPUB in vertical mode, we want to handle vertical swipes for chapter navigation
                    if (publicationType == PublicationType.EPUB) {
                        // Let the child WebView handle the vertical swipe for chapter navigation
                        return false
                    }
                    
                    return try {
                        super.onInterceptTouchEvent(swapXY(MotionEvent.obtain(ev)))
                    } catch (ex: IllegalArgumentException) {
                        Timber.e(ex)
                        false
                    }
                } else {
                    // Horizontal swipe — ignore so child views handle it
                    return false
                }
            }
        }

        return false
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (publicationType == PublicationType.EPUB) {
            if (ev.action == MotionEvent.ACTION_DOWN) {
                return false
            }
        }

        return try {
            super.onTouchEvent(swapXY(MotionEvent.obtain(ev)))
        } catch (ex: IllegalArgumentException) {
            Timber.e(ex)
            false
        }
    }

    private class VerticalPageTransformer : PageTransformer {
        override fun transformPage(view: View, position: Float) {
            when {
                position < -1 -> view.alpha = 0f
                position <= 1 -> {
                    view.alpha = 1f
                    view.translationX = view.width * -position
                    val yPosition = position * view.height
                    view.translationY = yPosition
                }
                else -> view.alpha = 0f
            }
        }
    }
}
