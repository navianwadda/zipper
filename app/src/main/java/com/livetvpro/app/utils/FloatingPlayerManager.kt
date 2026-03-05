package com.livetvpro.app.utils

object FloatingPlayerManager {
    
    private var preferencesManager: com.livetvpro.app.data.local.PreferencesManager? = null
    
    private val activeFloatingPlayers = mutableMapOf<String, PlayerMetadata>()
    
    data class PlayerMetadata(
        val instanceId: String,
        val contentName: String,
        val contentType: String,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Initialize with PreferencesManager
     * Call this from Application class or before first use
     */
    fun initialize(prefsManager: com.livetvpro.app.data.local.PreferencesManager) {
        this.preferencesManager = prefsManager
        android.util.Log.d("FloatingPlayerManager", "Initialized with max players: ${getMaxPlayerCount()}")
    }
    
    /**
     * Get the maximum allowed players from user settings.
     * Disable (old value=0, new value=1) both map to 1.
     */
    private fun getMaxAllowedPlayers(): Int {
        val maxPlayers = preferencesManager?.getMaxFloatingWindows() ?: 1
        val effective = if (maxPlayers <= 0) 1 else maxPlayers
        android.util.Log.d("FloatingPlayerManager", "Max allowed players: $effective")
        return effective
    }
    
    fun canAddNewPlayer(): Boolean {
        val maxAllowed = getMaxAllowedPlayers()
        val canAdd = activeFloatingPlayers.size < maxAllowed
        android.util.Log.d("FloatingPlayerManager", 
            "Can add new player: $canAdd (current: ${activeFloatingPlayers.size}, max: $maxAllowed)")
        return canAdd
    }
    
    fun getActivePlayerCount(): Int {
        return activeFloatingPlayers.size
    }
    
    fun getMaxPlayerCount(): Int {
        return getMaxAllowedPlayers()
    }
    
    fun addPlayer(instanceId: String, contentName: String, contentType: String) {
        val maxAllowed = getMaxAllowedPlayers()
        if (activeFloatingPlayers.size >= maxAllowed) {
            android.util.Log.w("FloatingPlayerManager", 
                "Cannot add player '$contentName': limit reached (${activeFloatingPlayers.size}/$maxAllowed)")
            return
        }
        
        val metadata = PlayerMetadata(instanceId, contentName, contentType)
        activeFloatingPlayers[instanceId] = metadata
        android.util.Log.d("FloatingPlayerManager", 
            "Added player '$contentName': ${activeFloatingPlayers.size}/$maxAllowed active")
    }
    
    fun removePlayer(instanceId: String) {
        val removed = activeFloatingPlayers.remove(instanceId)
        if (removed != null) {
            android.util.Log.d("FloatingPlayerManager", 
                "Removed player '${removed.contentName}': ${activeFloatingPlayers.size}/${getMaxAllowedPlayers()} active")
        }
    }
    
    fun getAllPlayerIds(): List<String> {
        return activeFloatingPlayers.keys.toList()
    }
    
    fun getPlayerMetadata(instanceId: String): PlayerMetadata? {
        return activeFloatingPlayers[instanceId]
    }
    
    fun getAllPlayerMetadata(): List<PlayerMetadata> {
        return activeFloatingPlayers.values.toList()
    }
    
    fun hasPlayer(instanceId: String): Boolean {
        return activeFloatingPlayers.containsKey(instanceId)
    }
    
    fun hasAnyPlayers(): Boolean {
        return activeFloatingPlayers.isNotEmpty()
    }

    /** Returns the most recently created floating player's instanceId, or null if none active. */
    fun getLastPlayerId(): String? {
        return activeFloatingPlayers.keys.lastOrNull()
    }
    
    fun clearAll() {
        val count = activeFloatingPlayers.size
        activeFloatingPlayers.clear()
        android.util.Log.d("FloatingPlayerManager", "Cleared all $count players")
    }
}
