package com.hectordelgado.weatherfy

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.Exception
import kotlin.math.round

class MainActivity : AppCompatActivity() {

    private lateinit var cityTextView: TextView
    private lateinit var currentWeatherImageView: ImageView
    private lateinit var searchEditText: EditText
    private lateinit var degreeToggleButton: ToggleButton
    private lateinit var searchButton: Button
    private lateinit var loadingConstraintView: ConstraintLayout

    private var currentTemperature: Float = 100000f
    private var cityName: String = ""
    private var isFahrenheit: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Instantiate widgets
        cityTextView = findViewById(R.id.cityTextView)
        currentWeatherImageView = findViewById(R.id.currentWeatherImageView)
        searchEditText = findViewById(R.id.searchEditText)
        degreeToggleButton = findViewById(R.id.degreeToggleButton)
        searchButton = findViewById(R.id.searchButton)
        loadingConstraintView = findViewById(R.id.loadingConstraintLayout)

        // Initially hide loading View
        loadingConstraintView.visibility = View.GONE

        // Hides keyboard when background is clicked
        searchEditText.setOnFocusChangeListener { v, hasFocus ->
            if(!hasFocus) {
                hideKeyboard(v)
            }
        }

        // Update degree type
        degreeToggleButton.setOnCheckedChangeListener { buttonView, isChecked ->
            isFahrenheit = isChecked
        }

        searchButton.setOnClickListener{
            if (searchEditText.text.isEmpty()) {
                Toast.makeText(this, "Stupid Human!\nYou are doomed to die a cold miserable lonely death!", Toast.LENGTH_SHORT).show()
            } else {
                cityName = searchEditText.text.toString()
                searchEditText.text.clear()
                weatherDataLoadingStarted()
                GlobalScope.launch {
                    getWeatherData(getUrlForCity("$cityName,US"))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // Get devices LocationManager
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Check if we have location access permissions
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_DENIED) {

            // Get latitude and longitude
            val currentLocation: Location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val latitude = currentLocation.latitude.toInt()
            val longitude = currentLocation.longitude.toInt()

            cityName = "Current Weather"
            weatherDataLoadingStarted()
            GlobalScope.launch {
                getWeatherData(getUrlForGeolocation(latitude, longitude))
            }

        } else {
            Toast.makeText(this, "GPS Tracking Disabled. Please allow in system settings.", Toast.LENGTH_SHORT).show()
        }

    }

    private fun getUrlForCity(cityName: String): String {
        return "https://api.openweathermap.org/data/2.5/weather?q=$cityName&appid=${resources.getString(R.string.weathermap_api_key)}"
    }

    private fun getUrlForGeolocation(latitude: Int, longitude: Int): String {
        return "https://api.openweathermap.org/data/2.5/weather?lat=$latitude&lon=$longitude&appid=${resources.getString(R.string.weathermap_api_key)}"
    }

    private fun convertKelvinToCelsius(temp: Float): Float {
        return temp - 273.15f
    }

    private fun convertKelvinToFahrenheit(temp: Float): Float {
        return (temp - 273.15f) * (9.0f/5.0f) + 32.0f
    }

    private fun convertCelsiusToFahrenheit(temp: Float): Float {
        return (temp * (9.0f/5.0f)) + 32.0f
    }

    private fun convertFahrenheitToCelsius(temp: Float): Float {
        return (temp - 32.0f) * (5.0f/9.0f)
    }

    private fun updateUI() {
        val degreeSign = when(isFahrenheit) {
            true -> "°F"
            else -> "°C"
        }
        val formattedTemperature = round(currentTemperature * 100) / 100
        val newLabel = "$cityName, $formattedTemperature$degreeSign"
        cityTextView.text = newLabel
    }

    private fun weatherDataLoadingStarted() {
        loadingConstraintView.visibility = View.VISIBLE
        degreeToggleButton.visibility = View.GONE
        searchButton.visibility = View.GONE
    }

    private fun weatherDataLoadingComplete() {
        loadingConstraintView.visibility = View.GONE
        degreeToggleButton.visibility = View.VISIBLE
        searchButton.visibility = View.VISIBLE
    }

    private fun hideKeyboard(view: View) {
        val inputMethodManager: InputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }


    private suspend fun getWeatherData(url: String) = withContext(Dispatchers.IO) {
        Log.d("8bt", "Making HTTP request for $url")


        try {
            // Instantiate the RequestQueue.
            val queue = Volley.newRequestQueue(this@MainActivity)

            // Request a string response from the provided URL.
            val stringRequest = StringRequest(Request.Method.GET, url,
                Response.Listener<String> {

                    // Convert string response to a JSON Object
                    val jsonObject = JSONObject(it)

                    // Get temperature in Kelvin
                    val temperatureInKelvin = jsonObject.getJSONObject("main").get("temp").toString().toFloat()

                    // Convert Kelvin to Celsius or Fahrenheit
                    currentTemperature = if (isFahrenheit) {
                        convertKelvinToFahrenheit(temperatureInKelvin)
                    } else {
                        convertKelvinToCelsius(temperatureInKelvin)
                    }

                    weatherDataLoadingComplete()
                    updateUI()

                    Log.d("8bt", "JSON Object for $cityName: $jsonObject")
                    Log.d("8bt", "Temperature for $cityName $temperatureInKelvin k")
                },
                Response.ErrorListener {
                    Toast.makeText(this@MainActivity, "Could not get data for your city.", Toast.LENGTH_SHORT).show()
                    Log.d("8bt", "Error making HTTP request!")
                    Log.d("8bt", "${it.message}")
                })

            // Add the request to the RequestQueue
            queue.add(stringRequest)
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, "There was an internal error.", Toast.LENGTH_SHORT).show()
            Log.d("8bt", e.toString())
        }

    }


}


