package org.travelplanner.app.features.tripDetails.history

class ConflictException(val serverStateJson: String) : Exception("Конфликт версий")