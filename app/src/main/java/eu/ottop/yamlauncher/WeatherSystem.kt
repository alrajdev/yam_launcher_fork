package eu.ottop.yamlauncher

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class WeatherSystem {

    private val sharedPreferenceManager = SharedPreferenceManager()
    private val stringUtils = StringUtils()

    fun setGpsLocation(activity: Activity) {
        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                println("Location obtained")
                locationManager.removeUpdates(this)
            }

            override fun onFlushComplete(requestCode: Int) {
                super.onFlushComplete(requestCode)
            }
        }

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                1
            )
            return
        }


        locationManager.requestLocationUpdates(LocationManager.FUSED_PROVIDER, 0, 0f, locationListener)


        val currentLocation = locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)


        if (currentLocation != null) {
            sharedPreferenceManager.setWeatherLocation(activity, "latitude=${currentLocation.latitude}&longitude=${currentLocation.longitude}", sharedPreferenceManager.getWeatherRegion(activity))
        }


    }

    // Run within Dispatchers.IO from the outside (doesn't refresh properly otherwise)
    fun getSearchedLocations(searchTerm: String?) : MutableList<Map<String, String>> {
        val foundLocations = mutableListOf<Map<String, String>>()

            val url = URL("https://geocoding-api.open-meteo.com/v1/search?name=$searchTerm&count=50&language=en&format=json")
            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "GET"
                try {
                inputStream.bufferedReader().use {
                    val response = it.readText()
                    println("yo")
                    val jsonObject = JSONObject(response)
                    val resultArray = jsonObject.getJSONArray("results")

                    for (i in 0 until resultArray.length()) {
                        val resultObject: JSONObject = resultArray.getJSONObject(i)

                        foundLocations.add(mapOf(
                            "name" to resultObject.getString("name"),
                            "latitude" to resultObject.getDouble("latitude").toString(),
                            "longitude" to resultObject.getDouble("longitude").toString(),
                            "country" to resultObject.optString("country", resultObject.optString("country_code","")),
                            "region" to stringUtils.addEndTextIfNotEmpty(resultObject.optString("admin2", resultObject.optString("admin1",resultObject.optString("admin3",""))), ", ")
                        ))
                    }
                }
            }catch (e: Exception){
                    e.printStackTrace()
            }
        }
        return foundLocations
    }

    fun getTemp(context: Context) : String {

            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val tempUnits = preferences.getString("tempUnits", "celsius")
            var currentWeather = ""

            val location = sharedPreferenceManager.getWeatherLocation(context)
        if (location != null) {
            if (location.isNotEmpty()) {
                val url = URL("https://api.open-meteo.com/v1/forecast?$location&temperature_unit=${tempUnits}&current=temperature_2m,weather_code")
                with(url.openConnection() as HttpURLConnection) {
                    requestMethod = "GET"

                    inputStream.bufferedReader().use {
                        val response = it.readText()

                        val jsonObject = JSONObject(response)

                        val currentData = jsonObject.getJSONObject("current")

                        var weatherType = ""

                        when (currentData.getInt("weather_code")) {
                            0, 1 -> {
                                weatherType = "☀\uFE0E"
                            }
                            2, 3, 45, 48 -> {
                                weatherType = "☁\uFE0E"
                            }
                            51, 53, 55, 56, 57, 61, 63, 65, 67, 80, 81, 82 -> {
                                weatherType = "☂\uFE0E"
                            }
                            71, 73, 75, 77, 85, 86 -> {
                                weatherType = "❄\uFE0E"
                            }
                            95, 96, 99 -> {
                                weatherType = "⛈\uFE0E"
                            }

                        }

                        currentWeather = "$weatherType ${currentData.getInt("temperature_2m").toString()}"

                    }
                }
            }
        }

        return when (tempUnits) {
            "celsius" -> {
                stringUtils.addEndTextIfNotEmpty(currentWeather, "°C")
            }

            "fahrenheit" -> {
                stringUtils.addEndTextIfNotEmpty(currentWeather, "°F")
            }

            else -> {
                ""
            }
        }

    }
}