package com.livetvpro.app.utils

import android.app.Application
import android.view.View
import android.widget.TextView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.livetvpro.app.utils.DeviceUtils
object ErrorMessageConverter {
    fun getShortErrorMessage(error: Throwable?): String {
        if (error == null) return "Unknown error"
        val message = error.message?.lowercase() ?: ""
        return when {
            message.contains("unable to resolve host") -> "No internet"
            message.contains("timeout") -> "Connection timeout"
            message.contains("failed to connect") -> "Connection failed"
            message.contains("network") -> "Network error"
            message.contains("socket") -> "Connection lost"
            message.contains("ssl") || message.contains("certificate") -> "Security error"
            message.contains("404") -> "Not found"
            message.contains("500") || message.contains("502") || message.contains("503") -> "Server error"
            message.contains("401") || message.contains("403") -> "Access denied"
            message.isEmpty() -> "Unknown error"
            else -> "Connection error"
        }
    }
}
interface Refreshable {
    fun refreshData()
}
interface IRetryViewModel {
    val isLoading: LiveData<Boolean>
    val error: LiveData<String?>
    val isEmpty: LiveData<Boolean>
    fun retry()
    fun refresh()
    fun onResume()
}
abstract class RetryViewModel : ViewModel(), IRetryViewModel {
    private val _isLoading = MutableLiveData<Boolean>(false)
    override val isLoading: LiveData<Boolean> = _isLoading
    private val _error = MutableLiveData<String?>(null)
    override val error: LiveData<String?> = _error
    private val _isEmpty = MutableLiveData<Boolean>(false)
    override val isEmpty: LiveData<Boolean> = _isEmpty
    private var hasLoadedOnce = false
    private var isRefreshing = false
    protected fun startLoading() {
        _isLoading.value = true
        _error.value = null
        _isEmpty.value = false
    }
    protected fun finishLoading(dataIsEmpty: Boolean, error: Throwable? = null) {
        _isLoading.value = false
        if (error != null) {
            if (!hasLoadedOnce || isRefreshing) {
                _error.value = ErrorMessageConverter.getShortErrorMessage(error)
            }
        } else {
            hasLoadedOnce = true
            _error.value = null
            _isEmpty.value = dataIsEmpty
        }
        isRefreshing = false
    }
    protected fun resetForRetry() {
        hasLoadedOnce = false
        isRefreshing = false
        _error.value = null
        _isEmpty.value = false
    }
    protected fun shouldReloadOnResume(): Boolean {
        return hasLoadedOnce
    }
    protected fun setError(message: String) {
        _error.value = message
    }
    protected fun clearError() {
        _error.value = null
    }
    protected fun hasLoadedData(): Boolean {
        return hasLoadedOnce
    }
    abstract fun loadData()
    override fun retry() {
        resetForRetry()
        loadData()
    }
    override fun refresh() {
        isRefreshing = true
        loadData()
    }
    override fun onResume() {
        if (shouldReloadOnResume()) {
            loadData()
        }
    }
    fun dismissError() {
        clearError()
    }
}
abstract class AndroidRetryViewModel(application: Application) : AndroidViewModel(application), IRetryViewModel {
    private val _isLoading = MutableLiveData<Boolean>(false)
    override val isLoading: LiveData<Boolean> = _isLoading
    private val _error = MutableLiveData<String?>(null)
    override val error: LiveData<String?> = _error
    private val _isEmpty = MutableLiveData<Boolean>(false)
    override val isEmpty: LiveData<Boolean> = _isEmpty
    private var hasLoadedOnce = false
    private var isRefreshing = false
    protected fun startLoading() {
        _isLoading.value = true
        _error.value = null
        _isEmpty.value = false
    }
    protected fun finishLoading(dataIsEmpty: Boolean, error: Throwable? = null) {
        _isLoading.value = false
        if (error != null) {
            if (!hasLoadedOnce || isRefreshing) {
                _error.value = ErrorMessageConverter.getShortErrorMessage(error)
            }
        } else {
            hasLoadedOnce = true
            _error.value = null
            _isEmpty.value = dataIsEmpty
        }
        isRefreshing = false
    }
    protected fun resetForRetry() {
        hasLoadedOnce = false
        isRefreshing = false
        _error.value = null
        _isEmpty.value = false
    }
    protected fun shouldReloadOnResume(): Boolean {
        return hasLoadedOnce
    }
    protected fun setError(message: String) {
        _error.value = message
    }
    protected fun clearError() {
        _error.value = null
    }
    protected fun hasLoadedData(): Boolean {
        return hasLoadedOnce
    }
    abstract fun loadData()
    override fun retry() {
        resetForRetry()
        loadData()
    }
    override fun refresh() {
        isRefreshing = true
        loadData()
    }
    override fun onResume() {
        if (shouldReloadOnResume()) {
            loadData()
        }
    }
    fun dismissError() {
        clearError()
    }
}
object RetryHandler {
    fun setupGlobal(
        lifecycleOwner: LifecycleOwner,
        viewModel: IRetryViewModel,
        activity: androidx.appcompat.app.AppCompatActivity,
        contentView: View,
        swipeRefresh: SwipeRefreshLayout? = null,
        progressBar: View? = null,
        emptyView: View? = null
    ) {
        val errorOverlay = activity.findViewById<View>(com.livetvpro.app.R.id.global_error_overlay)
        val errorText = activity.findViewById<TextView>(com.livetvpro.app.R.id.global_error_text)
        val retryButton = activity.findViewById<View>(com.livetvpro.app.R.id.global_retry_button)
        val isTv = DeviceUtils.isTvDevice
        errorOverlay.visibility = View.GONE
        contentView.visibility = View.VISIBLE
        emptyView?.visibility = View.GONE
        swipeRefresh?.isEnabled = !isTv
        retryButton.setOnClickListener { viewModel.retry() }
        swipeRefresh?.setOnRefreshListener { viewModel.refresh() }
        viewModel.isLoading.observe(lifecycleOwner) { isLoading ->
            progressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
            swipeRefresh?.isRefreshing = isLoading
        }
        viewModel.isEmpty.observe(lifecycleOwner) { isEmpty ->
            emptyView?.visibility = if (isEmpty) View.VISIBLE else View.GONE
        }
        viewModel.error.observe(lifecycleOwner) { error ->
            if (error != null) {
                errorOverlay.visibility = View.VISIBLE
                errorText.text = error
                contentView.visibility = View.GONE
                emptyView?.visibility = View.GONE
                if (!isTv) swipeRefresh?.isEnabled = true
            } else {
                errorOverlay.visibility = View.GONE
                contentView.visibility = View.VISIBLE
                if (!isTv) swipeRefresh?.isEnabled = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                errorOverlay.visibility = View.GONE
                contentView.visibility = View.VISIBLE
            }
        })
    }
    fun setupWithRefresh(
        lifecycleOwner: LifecycleOwner,
        viewModel: IRetryViewModel,
        swipeRefresh: SwipeRefreshLayout?,
        errorView: View,
        errorText: TextView,
        retryButton: View,
        contentView: View,
        progressBar: View,
        emptyView: View? = null
    ) {
        retryButton.setOnClickListener {
            viewModel.retry()
        }
        swipeRefresh?.setOnRefreshListener {
            viewModel.refresh()
        }
        viewModel.isLoading.observe(lifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            swipeRefresh?.isRefreshing = isLoading
        }
        viewModel.isEmpty.observe(lifecycleOwner) { isEmpty ->
            emptyView?.visibility = if (isEmpty) View.VISIBLE else View.GONE
            contentView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
        viewModel.error.observe(lifecycleOwner) { error ->
            if (error != null) {
                errorView.visibility = View.VISIBLE
                errorText.text = error
                retryButton.visibility = View.VISIBLE
                contentView.visibility = View.GONE
                emptyView?.visibility = View.GONE
                swipeRefresh?.isEnabled = false
            } else {
                errorView.visibility = View.GONE
                retryButton.visibility = View.GONE
                if (viewModel.isEmpty.value != true) {
                    contentView.visibility = View.VISIBLE
                }
                swipeRefresh?.isEnabled = true
            }
        }
    }
    fun setup(
        lifecycleOwner: LifecycleOwner,
        viewModel: IRetryViewModel,
        errorView: View,
        errorText: TextView,
        retryButton: View,
        contentView: View,
        progressBar: View,
        emptyView: View? = null
    ) {
        setupWithRefresh(
            lifecycleOwner = lifecycleOwner,
            viewModel = viewModel,
            swipeRefresh = null,
            errorView = errorView,
            errorText = errorText,
            retryButton = retryButton,
            contentView = contentView,
            progressBar = progressBar,
            emptyView = emptyView
        )
    }
}
