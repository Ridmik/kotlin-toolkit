/*
 * Module: r2-navigator-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann, Mostapha Idoubihi, Paul Stoica
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

@file:OptIn(InternalReadiumApi::class)

package org.readium.r2.navigator.pager

import android.annotation.SuppressLint
import android.graphics.PointF
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.*
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.os.BundleCompat
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.readium.r2.navigator.R
import org.readium.r2.navigator.R2BasicWebView
import org.readium.r2.navigator.R2WebView
import org.readium.r2.navigator.databinding.ReadiumNavigatorViewpagerFragmentEpubBinding
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubNavigatorViewModel
import org.readium.r2.navigator.epub.EpubSettings
import org.readium.r2.navigator.extensions.htmlId
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.InternalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl
import timber.log.Timber

@OptIn(ExperimentalReadiumApi::class)
internal class R2EpubPageFragment : Fragment() {

    private val resourceUrl: AbsoluteUrl?
        get() = BundleCompat.getParcelable(requireArguments(), "url", AbsoluteUrl::class.java)

    internal val link: Link?
        get() = BundleCompat.getParcelable(requireArguments(), "link", Link::class.java)

    private var pendingLocator: Locator? = null

    private val positionCount: Long
        get() = requireArguments().getLong("positionCount")

    var webView: R2WebView? = null
        private set

    private lateinit var containerView: View
    private val viewModel: EpubNavigatorViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private var _binding: ReadiumNavigatorViewpagerFragmentEpubBinding? = null
    private val binding get() = _binding!!

    private var isLoading: Boolean = false
    private val _isLoaded = MutableStateFlow(false)

    internal fun setFontSize(fontSize: Double) {
        textZoom = (fontSize * 100).roundToInt()
    }

    private var textZoom: Int = 100
        set(value) {
            field = value
            webView?.settings?.textZoom = value
        }

    /**
     * Indicates whether the resource is fully loaded in the web view.
     */
    @InternalReadiumApi
    val isLoaded: StateFlow<Boolean>
        get() = _isLoaded.asStateFlow()

    /**
     * Waits for the page to be loaded.
     */
    @InternalReadiumApi
    suspend fun awaitLoaded() {
        isLoaded.first { it }
    }

    private val navigator: EpubNavigatorFragment?
        get() = parentFragment as? EpubNavigatorFragment

    private val shouldApplyInsetsPadding: Boolean
        get() = navigator?.config?.shouldApplyInsetsPadding ?: true

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(textZoomBundleKey, textZoom)

        super.onSaveInstanceState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        savedInstanceState
            ?.getInt(textZoomBundleKey)
            ?.takeIf { it > 0 }
            ?.let { textZoom = it }
    }

    private val onFlingNavigationCallBack: OnFlingNavigationCallBack by lazy {
        object: OnFlingNavigationCallBack {

            private var isPostDelayed = false

            override fun loadPrevious() {
                if(viewModel.triScrollState.value == EpubSettings.ReaderScroll.SCROLL.variant) {
                    onScrollLoadPrevious()
                } else if(viewModel.triScrollState.value == EpubSettings.ReaderScroll.MIXED.variant) {
                    val viewPager = navigator?.resourcePager?:return
                    viewPager.setCurrentItem(viewPager.currentItem - 1, /*smoothScroll=*/true)
                }
            }

            private fun onScrollLoadPrevious() {
                if(isPostDelayed) {
                    return
                }
                val viewPager = navigator?.resourcePager
                if(viewPager?.currentItem == 0) {
                    Timber.tag(TAG).e("reached the first page, returning...")
                    return
                }
                isPostDelayed = true

                _binding?.top?.visibility = View.VISIBLE
                _binding?.root?.postDelayed(
                    {
                        _binding?.top?.visibility = View.INVISIBLE

                        // navigator?.goBackward(animated = true)
                        val viewPager = navigator?.resourcePager?:return@postDelayed
                        viewPager.setCurrentItem(viewPager.currentItem - 1, /*smoothScroll=*/true)
                        isPostDelayed = false
                    }, 250
                )
            }

            override fun loadNext() {
                if(viewModel.triScrollState.value == EpubSettings.ReaderScroll.SCROLL.variant) {
                    onScrollLoadNext()
                } else if(viewModel.triScrollState.value == EpubSettings.ReaderScroll.MIXED.variant) {
                    val viewPager = navigator?.resourcePager?:return
                    viewPager.setCurrentItem(viewPager.currentItem + 1, /*smoothScroll=*/true)
                }
            }

            private fun onScrollLoadNext() {
                if(isPostDelayed) {
                    return
                }
                val viewPager = navigator?.resourcePager

                val currentItem = viewPager?.currentItem?:return
                val totalCount = viewPager.count
                val lastItem = totalCount - 1
                Timber.tag(TAG).e("onScrollLoadNext currentItem: $currentItem, lastItem: $lastItem")

                if(currentItem == lastItem) {
                    Timber.tag(TAG).e("reached the last page, returning...")
                    return
                }
                isPostDelayed = true


                _binding?.bottom?.visibility = View.VISIBLE
                _binding?.root?.postDelayed({
                    _binding?.bottom?.visibility = View.INVISIBLE

                    // navigator?.goForward(animated = true)
                    val viewPager = navigator?.resourcePager?:return@postDelayed
                    viewPager.setCurrentItem(viewPager.currentItem + 1, /*smoothScroll=*/true)
                    isPostDelayed = false
                }, 250)
            }
        }
    }



    private val mGestureDetector: GestureDetector by lazy {
        GestureDetector(requireContext(), object :
            GestureDetector.SimpleOnGestureListener() {


            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                val webView = webView ?: return false
                val topLoader = _binding?.top?:return false
                val bottomLoader = _binding?.bottom?:return false

                val atTop = webView.scrollY == 0
                val atBottom = webView.scrollY + webView.height >= webView.contentHeight



                if (atTop) {
                    val dy = (e2.y - (e1?.y ?: e2.y))
                    if (dy > 0) { // pulling down
                        dragOffset = dy.coerceAtMost(DRAG_THRESHOLD * 1.5f)
                        topLoader.visibility = View.VISIBLE
                        topLoader.translationY = dragOffset - topLoader.height

                        val progress = (dragOffset / DRAG_THRESHOLD).coerceIn(0f, 1f)
                        _binding?.topProgress?.progress = (progress * 100).toInt()
                    }
                } else if (atBottom) {
                    val dy = (e2.y - (e1?.y ?: e2.y))
                    if (dy < 0) { // pulling up
                        dragOffset = (-dy).coerceAtMost(DRAG_THRESHOLD * 1.5f)
                        bottomLoader.visibility = View.VISIBLE
                        bottomLoader.translationY = -dragOffset + bottomLoader.height

                        val progress = (dragOffset / DRAG_THRESHOLD).coerceIn(0f, 1f)
                        _binding?.bottomProgress?.progress = (progress * 100).toInt()
                    }
                }
                return false
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if(viewModel.triScrollState.value != EpubSettings.ReaderScroll.SCROLL.variant) {
                    return false
                }

                if(webView == null) return false

                val diffY = e2.y - (e1?.y ?:0f)

                if (abs(diffY) < SWIPE_THRESHOLD) {
                    return false
                }
                if(abs(velocityY) < SWIPE_VELOCITY_THRESHOLD) {
                    return false
                }
                // guaranteed powerful fling
                val webView = webView!!
                val scrollY = webView.scrollY
                val contentHeight = (webView.contentHeight)
                val viewHeight = webView.height

                when {
                    scrollY == 0 -> {
                        Timber.tag("WebViewFling").d("Fling at TOP")
                        // 2 cases:
                        // 1 small content
                        if(viewHeight == contentHeight) {
                            if(diffY > 0) {
                                onFlingNavigationCallBack.loadPrevious()
                            } else {
                                onFlingNavigationCallBack.loadNext()
                            }
                        } else {
                            // 2 large content
                            onFlingNavigationCallBack.loadPrevious()
                        }

                        return true
                    }
                    scrollY + viewHeight >= contentHeight -> {
                        Timber.tag("WebViewFling").d("Fling at BOTTOM")
                        onFlingNavigationCallBack.loadNext()
                        return true
                    }
                    else -> {
                        Timber.tag("WebViewFling").d("Fling somewhere in middle")
                        return false
                    }
                }
                return super.onFling(e1, e2, velocityX, velocityY)
            }
        })
    }

    private fun resetEdgeLoaders(event: MotionEvent) {
        val ok =
            (event.action == MotionEvent.ACTION_UP) || (event.action == MotionEvent.ACTION_CANCEL)

        if(!ok) {
            return
        }

        val topLoader = _binding?.top ?: return
        val bottomLoader = _binding?.bottom ?: return

        // Always animate back to hidden
        _binding?.root?.postDelayed({
            topLoader.animate()
                .translationY(-topLoader.height.toFloat())
                .setDuration(200)
                .withEndAction {
                    topLoader.visibility = View.GONE
                }

            bottomLoader.animate()
                .translationY(bottomLoader.height.toFloat())
                .setDuration(200)
                .withEndAction {
                    bottomLoader.visibility = View.GONE
                }

            _binding?.topProgress?.progress = 0
            _binding?.bottomProgress?.progress = 0
            dragOffset = 0f
        }, 250)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingLocator = BundleCompat.getParcelable(
            requireArguments(),
            "initialLocator",
            Locator::class.java
        )
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = ReadiumNavigatorViewpagerFragmentEpubBinding.inflate(inflater, container, false)
        containerView = binding.root

        val webView = binding.webView
        this.webView = webView

        webView.setOnTouchListener { _, event ->
            if(viewModel.triScrollState.value == EpubSettings.ReaderScroll.SCROLL.variant) {
                resetEdgeLoaders(event)
                mGestureDetector.onTouchEvent(event)
            }
            false
        }




        webView.visibility = View.INVISIBLE
        navigator?.webViewListener?.let { listener ->
            webView.listener = listener

            link?.let { link ->
                // Setup custom Javascript interfaces.
                for ((name, obj) in listener.javascriptInterfacesForResource(link)) {
                    if (obj != null) {
                        webView.addJavascriptInterface(obj, name)
                    }
                }
            }
        }

        webView.disablePageTurnsWhileScrolling =
            navigator?.config?.disablePageTurnsWhileScrolling ?: false
        webView.settings.javaScriptEnabled = true
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.textZoom = textZoom
        webView.resourceUrl = resourceUrl
        webView.setPadding(0, 0, 0, 0)
        webView.addJavascriptInterface(webView, "Android")

        var endReached = false
        webView.setOnOverScrolledCallback(object : R2BasicWebView.OnOverScrolledCallback {
            override fun onOverScrolled(
                scrollX: Int,
                scrollY: Int,
                clampedX: Boolean,
                clampedY: Boolean,
            ) {
                activity ?: return
                val metrics = DisplayMetrics()

                val topDecile = webView.contentHeight - 1.15 * metrics.heightPixels
                val bottomDecile = (webView.contentHeight - metrics.heightPixels).toDouble()

                when (scrollY.toDouble()) {
                    in topDecile..bottomDecile -> {
                        if (!endReached) {
                            endReached = true
                            webView.listener?.onPageEnded(endReached)
                        }
                    }
                    else -> {
                        if (endReached) {
                            endReached = false
                            webView.listener?.onPageEnded(endReached)
                        }
                    }
                }
            }
        })

        webView.webViewClient = object : WebViewClientCompat() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                (webView as? R2BasicWebView)?.shouldOverrideUrlLoading(request) ?: false

            override fun shouldOverrideKeyEvent(view: WebView, event: KeyEvent): Boolean {
                // Do something with the event here
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                onPageFinished()

                link?.let {
                    webView.listener?.onResourceLoaded(webView, it)
                }

                webView.onContentReady {
                    onLoadPage()
                }
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                (webView as? R2BasicWebView)?.shouldInterceptRequest(view, request)
        }

        webView.isHapticFeedbackEnabled = false
        webView.isLongClickable = false
        webView.setOnLongClickListener {
            false
        }

        resourceUrl?.let {
            isLoading = true
            _isLoaded.value = false
            webView.loadUrl(it.toString())
        }

        setupPadding()

        // Forward a tap event when the web view is not ready to propagate the taps. This allows
        // to toggle a navigation UI while a page is loading, for example.
        binding.root.setOnClickListenerWithPoint { _, point ->
            webView.listener?.onTap(point)
        }

        return containerView
    }

    private var isPageFinished = false
    private val pendingPageFinished = mutableListOf<() -> Unit>()

    /**
     * Will run the given [action] when the content of the [WebView] is loaded.
     */
    fun whenPageFinished(action: () -> Unit) {
        if (isPageFinished) {
            action()
        } else {
            pendingPageFinished.add(action)
        }
    }

    private fun onPageFinished() {
        isPageFinished = true
        pendingPageFinished.forEach { it() }
        pendingPageFinished.clear()
    }

    /**
     * Will run the given [action] when the content of the [WebView] is fully laid out.
     */
    private fun WebView.onContentReady(action: () -> Unit) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.VISUAL_STATE_CALLBACK)) {
            WebViewCompat.postVisualStateCallback(this, 0) {
                action()
            }
        } else {
            // On older devices, there's no reliable way to guarantee the page is fully laid out.
            // As a workaround, we run a dummy JavaScript, then wait for a short delay before
            // assuming it's ready.
            evaluateJavascript("true") {
                postDelayed(500, action)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val lifecycleOwner = viewLifecycleOwner
        lifecycleOwner.lifecycleScope.launch {
            viewModel.triScrollState
                .flowWithLifecycle(lifecycleOwner.lifecycle)
                .collectLatest { webView?.scrollModeFlow?.value = it }
        }
    }

    override fun onDestroyView() {
        webView?.listener = null
        _binding = null

        super.onDestroyView()
    }

    override fun onDetach() {
        super.onDetach()

        // Prevent the web view from leaking when its parent is detached.
        // See https://stackoverflow.com/a/19391512/1474476
        webView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.removeAllViews()
            wv.destroy()
        }
    }

    private fun setupPadding() = viewLifecycleOwner.lifecycleScope.launch {
        viewModel.triScrollState.collectLatest { triScroll ->
            if(triScroll == EpubSettings.ReaderScroll.SCROLL.variant) {
                //case scroll: Absolutely no padding
                containerView.setPadding(0, 0, 0, 0)
                return@collectLatest
            }

            val margin =
                resources.getDimension(R.dimen.readium_navigator_epub_vertical_padding)
                    .toInt()

            var top = margin
            var bottom = margin

            if(triScroll == EpubSettings.ReaderScroll.MIXED.variant) {
                // case: Mixed
                containerView.setPadding(0, top, 0, bottom)
                return@collectLatest
            }
            // case: Slide
            val window = activity?.window ?: return@collectLatest

            // Add additional padding to take into account the display cutout, if needed.
            if (
                shouldApplyInsetsPadding &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P &&
                window.attributes.layoutInDisplayCutoutMode != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            ) {
                // Request the display cutout insets from the decor view because the ones given by
                // setOnApplyWindowInsetsListener are not always correct for preloaded views.
                window.decorView.rootWindowInsets?.displayCutout?.let { displayCutoutInsets ->
                    top += displayCutoutInsets.safeInsetTop
                    bottom += displayCutoutInsets.safeInsetBottom
                }
            }


            containerView.setPadding(0, top, 0, bottom)
        }
    }

    internal val paddingTop: Int get() = containerView.paddingTop
    internal val paddingBottom: Int get() = containerView.paddingBottom

    private val isCurrentResource: Boolean get() {
        val epubNavigator = navigator ?: return false
        val currentFragment = (epubNavigator.resourcePager.adapter as? R2PagerAdapter)?.getCurrentFragment() as? R2EpubPageFragment ?: return false
        return tag == currentFragment.tag
    }

    private fun onLoadPage() {
        if (!isLoading) return
        isLoading = false
        _isLoaded.value = true

        if (view == null) return

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                val webView = requireNotNull(webView)
                webView.visibility = View.VISIBLE

                pendingLocator
                    ?.let { locator ->
                        loadLocator(
                            webView,
                            requireNotNull(navigator).overflow.value.readingProgression,
                            locator
                        )
                    }
                    .also { pendingLocator = null }

                link?.let {
                    webView.listener?.onPageLoaded(webView, it)
                }
            }
        }
    }

    internal fun loadLocator(locator: Locator) {
        if (!isLoaded.value) {
            pendingLocator = locator
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                val webView = requireNotNull(webView)
                val epubNavigator = requireNotNull(navigator)
                loadLocator(webView, epubNavigator.overflow.value.readingProgression, locator)
                webView.listener?.onProgressionChanged()
            }
        }
    }

    private suspend fun loadLocator(
        webView: R2WebView,
        readingProgression: ReadingProgression,
        locator: Locator,
    ) {
        if (locator.text.highlight != null) {
            if (webView.scrollToLocator(locator)) {
                return
            }
        }

        val htmlId = locator.locations.htmlId
        if (htmlId != null && webView.scrollToId(htmlId)) {
            return
        }

        var progression = locator.locations.progression ?: 0.0

        // We need to reverse the progression with RTL because the Web View
        // always scrolls from left to right, no matter the reading direction.
        progression =
            if ( (webView.scrollMode != EpubSettings.ReaderScroll.SLIDE.variant) || readingProgression == ReadingProgression.LTR) {
                progression
            } else {
                1 - progression
            }

        if (webView.scrollMode != EpubSettings.ReaderScroll.SLIDE.variant) {
            webView.scrollToPosition(progression)
        } else {
            // Figure out the target web view "page" from the requested
            // progression.
            var item = (progression * webView.numPages).roundToInt()
            if (readingProgression == ReadingProgression.RTL && item > 0) {
                item -= 1
            }
            webView.setCurrentItem(item, false)
        }
    }

    fun runJavaScript(script: String, callback: ((String) -> Unit)? = null) {
        whenPageFinished {
            requireNotNull(webView).runJavaScript(script, callback)
        }
    }

    suspend fun runJavaScriptSuspend(javascript: String): String = suspendCoroutine { cont ->
        runJavaScript(javascript) { result ->
            cont.resume(result)
        }
    }

    companion object {
        private const val textZoomBundleKey = "org.readium.textZoom"

        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100

        private var dragOffset = 0f
        private const val DRAG_THRESHOLD = 200f

        private const val TAG = "R2EpubPageFragment"

        fun newInstance(
            url: AbsoluteUrl,
            link: Link? = null,
            initialLocator: Locator? = null,
            positionCount: Int = 0,
        ): R2EpubPageFragment =
            R2EpubPageFragment().apply {
                arguments = Bundle().apply {
                    putParcelable("url", url)
                    putParcelable("link", link)
                    putParcelable("initialLocator", initialLocator)
                    putLong("positionCount", positionCount.toLong())
                }
            }
    }
}

/**
 * Same as setOnClickListener, but will also report the tap point in the view.
 */
private fun View.setOnClickListenerWithPoint(action: (View, PointF) -> Unit) {
    var point = PointF()

    setOnTouchListener { _, event ->
        if (event.action == MotionEvent.ACTION_DOWN) {
            point = PointF(event.x, event.y)
        }
        false
    }

    setOnClickListener {
        action(it, point)
    }
}

internal interface OnFlingNavigationCallBack {
    fun loadPrevious()
    fun loadNext()
}