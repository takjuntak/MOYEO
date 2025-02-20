package com.neungi.moyeo.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.neungi.domain.model.ApiResult
import com.neungi.domain.model.ApiStatus
import com.neungi.domain.model.Festival
import com.neungi.domain.model.LoginInfo
import com.neungi.domain.model.Place
import com.neungi.domain.usecase.GetFollowedPlacesUseCase
import com.neungi.domain.usecase.GetInviteUseCase
import com.neungi.domain.usecase.GetSearchPlaceUseCase
import com.neungi.domain.usecase.GetUserInfoUseCase
import com.neungi.domain.usecase.PlaceFollowUseCase
import com.neungi.domain.usecase.SaveNotificationUseCase
import com.neungi.domain.usecase.SetFCMUseCase
import com.neungi.domain.usecase.SetUserInfoUseCase
import com.neungi.moyeo.service.NotificationEventBus
import com.neungi.moyeo.util.EmptyState
import com.neungi.moyeo.views.aiplanning.viewmodel.AiPlanningUiEvent
import com.neungi.moyeo.views.aiplanning.viewmodel.SearchUiState
import com.neungi.moyeo.views.setting.viewmodel.SettingUiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getSearchPlaceUseCase: GetSearchPlaceUseCase,
    private val getUserInfoUseCase: GetUserInfoUseCase,
    private val setUserInfoUseCase: SetUserInfoUseCase,
    private val setFCMUseCase: SetFCMUseCase,
    private val followedPlacesUseCase: GetFollowedPlacesUseCase,
    private val saveNotificationUseCase: SaveNotificationUseCase,
    private val placeFollowUseCase : PlaceFollowUseCase,
    private val getInviteUseCase: GetInviteUseCase
) : ViewModel() {

    init {
        getDeviceId()
        getFCMToken()
        login()
        viewModelScope.launch {
            NotificationEventBus.notificationReceived.collect {
                planTriggerRefresh()
            }
        }
    }

    private val _settingUiEvent = MutableSharedFlow<SettingUiEvent>()
    val settingUiEvent = _settingUiEvent.asSharedFlow()

    private val _bnvState = MutableStateFlow<Boolean>(true)
    val bnvState = _bnvState.asStateFlow()

    fun setBnvState(isVisible: Boolean) {
        _bnvState.value = isVisible
    }

    private val _loadingState = MutableStateFlow<Boolean>(false)
    val loadingState = _loadingState.asStateFlow()

    fun setLoadingState(isLoading: Boolean){
        _loadingState.update { isLoading }
    }

    //관광지 검색 결과
    private val _placeSearchResult = MutableStateFlow<List<Place>>(emptyList())
    val placeSearchResult = _placeSearchResult.asStateFlow()

    private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState())
    val searchUiState = _searchUiState.asStateFlow()

    private val _userLoginInfo = MutableStateFlow<LoginInfo?>(null)
    val userLoginInfo = _userLoginInfo.asStateFlow()

    //firebase
    private val _fcmToken = MutableStateFlow<String>("")
    val fcmToken = _fcmToken.asStateFlow()

    //deviceID
    private val _deviceID = MutableStateFlow<String>("")
    val deviceID = _deviceID.asStateFlow()

    private val _searchFollowedPlaces = MutableStateFlow<List<Place>>(emptyList())
    val searchFollowedPlaces = _searchFollowedPlaces.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(false)
    val refreshTrigger = _refreshTrigger.asStateFlow()

    private val _refreshPlanTrigger = MutableStateFlow(false)
    val refreshPlanTrigger = _refreshPlanTrigger.asStateFlow()





    //관광지 검색창에서 팔로우한 지역 목록
    //Search place
    fun getFollowedPlaces(){
        viewModelScope.launch {
            followedPlacesUseCase().collectLatest {result ->
                when(result.status) {
                    ApiStatus.SUCCESS ->{
                        _searchFollowedPlaces.update {
                            result.data?: emptyList()
                        }
                    }
                    ApiStatus.ERROR -> {
                        _searchFollowedPlaces.update {
                            result.data?: emptyList()
                        }
                    }
                    ApiStatus.FAIL -> {
                        _searchFollowedPlaces.update {
                            result.data?: emptyList()
                        }
                    }
                    ApiStatus.LOADING -> {}
                }

            }

        }
    }

    fun onClickFollow(contentId:String){
        viewModelScope.launch {
            placeFollowUseCase(contentId).collect(){result->
                when(result.status){
                    ApiStatus.SUCCESS -> {
                        if (result.data == true) {
                            getFollowedPlaces()
                        }
                    }
                    ApiStatus.ERROR ->{}
                    ApiStatus.FAIL -> {}
                    ApiStatus.LOADING -> {}
                }
            }
        }
    }
    /*
    관광지 검색시
    검색창 텍스트 변경시
     */
    fun onSearchTextChanged(keyword:String?){
        Timber.d(keyword.toString())
        if(keyword.isNullOrBlank()){
            _searchUiState.update { it.copy( searchTextState = EmptyState.EMPTY) }
        }else{
            viewModelScope.launch {
                val result = getSearchPlaceUseCase.getSearchPlace(keyword)
                Timber.d("${result}")
                when (result.status) {
                    ApiStatus.SUCCESS -> {
                        result.data?.let { places ->
                            _placeSearchResult.value = places.toList()
                        }
                    }
                    ApiStatus.ERROR -> {
                        Timber.e(result.message)
                        _placeSearchResult.value = emptyList()
                    }
                    ApiStatus.FAIL -> {
                        _placeSearchResult.value = emptyList()
                    }
                    ApiStatus.LOADING -> {
                        // 로딩 상태 처리가 필요한 경우
                    }
                }
                _searchUiState.update { it.copy( searchTextState = EmptyState.NONE) }
            }
        }
    }

    fun clearSearchResult(){
        _searchUiState.update { it.copy( searchTextState = EmptyState.EMPTY) }
        _placeSearchResult.value = emptyList()
    }

    fun login(){
        viewModelScope.launch {
            Timber.d("Login")
            getUserInfoUseCase.getLoginInfo().first() { loginInfo ->
                _userLoginInfo.value = loginInfo
                when (_userLoginInfo.value == null) {
                    true -> _settingUiEvent.emit(SettingUiEvent.GetUserInfoFail)

                    else -> _settingUiEvent.emit(SettingUiEvent.GetUserInfoSuccess)
                }
                checkAndUpdateFCM()
                true
            }
        }
    }

    fun logout(){
        viewModelScope.launch {
            Timber.d("logout"+_userLoginInfo.value)
            setFCMUseCase.deleteFCMToken(_userLoginInfo.value!!.userId,_deviceID.value)
            _userLoginInfo.update {
                null
            }
            saveNotificationUseCase.clearNotification()

        }
    }

    //firebase
    fun getFCMToken(){
        // FCM 토큰 가져오기
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Timber.d("FCMToken: "+token)
                    _fcmToken.update { token }
                    checkAndUpdateFCM()
                }
            }
    }

    fun getDeviceId(){
        viewModelScope.launch {
            try {
                var dataStoreDeviceID = getUserInfoUseCase.getDeviceId().first()

                if (dataStoreDeviceID.isNullOrEmpty()) {
                    dataStoreDeviceID = UUID.randomUUID().toString()
                    setUserInfoUseCase.setDeviceInfo(dataStoreDeviceID)
                }
                _deviceID.update { dataStoreDeviceID}
                checkAndUpdateFCM()
            } catch (e: Exception) {
                Timber.e(e, "Error getting device ID")
            }
        }
    }

    private fun checkAndUpdateFCM() {
        val loginInfo = _userLoginInfo.value
        val deviceId = _deviceID.value
        val token = _fcmToken.value
        Timber.d("loginInfo : "+loginInfo.toString())
        Timber.d("deviceId : "+ deviceId)
        Timber.d("fcmToken : "+token)
        if (loginInfo != null && deviceId.isNotEmpty() && token.isNotEmpty()) {
            viewModelScope.launch {
                setFCMUseCase.registFCMToken(loginInfo.userId, deviceId, token)
            }
        }
    }

    fun triggerRefresh() {
        _refreshTrigger.value = true
    }

    fun offRefreshTrigger() {
        _refreshTrigger.value = false
    }

    fun planTriggerRefresh() {
        _refreshPlanTrigger.value = true
    }

    fun offRefreshPlanTrigger() {
        _refreshPlanTrigger.value = false
    }
}

