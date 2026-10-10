package com.dormmate.model

class RoomWithOccupants(
    val room: Room?,
    val occupantCount: Int,
    val occupantNames: List<String>?
) {
    fun isFull(): Boolean {
        return room != null && room.capacity > 0 && occupantCount >= room.capacity
    }

    fun getStatusText(): String {
        if (room == null) return "UNKNOWN"
        return if (isFull()) {
            "FULL ($occupantCount/${room.capacity})"
        } else if (room.capacity > 0) {
            "AVAILABLE ($occupantCount/${room.capacity})"
        } else {
            "AVAILABLE"
        }
    }
}
