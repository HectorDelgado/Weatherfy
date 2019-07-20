package com.hectordelgado.weatherfy

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.Exception
import kotlin.math.round

private const val LOCATION_REQUEST_CODE = 666   // Request code for location services

class MainActivity : AppCompatActivity() {

    private lateinit var cityTextView: TextView                             // Displays the city and current weather
    private lateinit var currentWeatherImageView: ImageView                 // Image based on the weather description
    private lateinit var searchEditText: EditText                           // Retrieves the city to search for
    private lateinit var degreeToggleButton: ToggleButton                   // Toggles between celsius and fahrenheit
    private lateinit var searchByCityButton: Button                         // Search weather by city name
    private lateinit var searchByLocationButton: Button                     // Search weather by location
    private lateinit var loadingConstraintView: ConstraintLayout            // Displays a Spinner ProgressBar
    private lateinit var fusedLocationClient: FusedLocationProviderClient   // Provides the best location client

    private var openWeatherMapAPIKey = ""               // Stores the OpenWeatherMap API Key
    private var temperatureInCelsius: Float = 0f        // Current temperature in celsius
    private var temperatureInFahrenheit: Float = 0f     // Current temperature in fahrenheit
    private var cityName: String = ""                   // Stores the city name to search for
    private var isFahrenheit: Boolean = false           // Keeps track of temperature state
    private var latitude: Double = 0.0                  // Stores the last known latitude
    private var longitude: Double = 0.0                 // Stores the last known longitude

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Instantiate widgets
        cityTextView = findViewById(R.id.cityTextView)
        currentWeatherImageView = findViewById(R.id.currentWeatherImageView)
        searchEditText = findViewById(R.id.searchEditText)
        degreeToggleButton = findViewById(R.id.degreeToggleButton)
        searchByCityButton = findViewById(R.id.searchByCityButton)
        searchByLocationButton = findViewById(R.id.searchByLocationButton)
        loadingConstraintView = findViewById(R.id.loadingConstraintLayout)
        loadingConstraintView.visibility = View.GONE

        // ENTER YOUR OPEN WEATHER MAP API KEY HERE!!!
        openWeatherMapAPIKey = resources.getString(R.string.weathermap_api_key)

        // Create instance of the FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        searchEditText.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                hideKeyboard(v)
            }
        }

        // Update degree measurement
        degreeToggleButton.setOnCheckedChangeListener { buttonView, isChecked ->
            isFahrenheit = isChecked
        }
    }

    override fun onStart() {
        super.onStart()
        prepareSearchByGeolocation()
    }

    /**
     * If the request code returned matches our request code and the results contains
     * permission granted, then prepare a weather search by geolocation.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_REQUEST_CODE -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.d("8bt", "Permission denied")
                    Toast.makeText(this, "Location Services Are Required To Get The Current Weather!", Toast.LENGTH_LONG).show()
                } else {
                    Log.d("8bt", "Permission Granted")
                    prepareSearchByGeolocation()
                }
            }
        }
    }

    /**
     * Prepares a weather search by city name if the soft keyboards enter button is pressed.
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when(keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                prepareSearchByCityName()
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }

    /**
     * Prepares a weather search by either city name or location.
     */
    fun searchButtonClicked(view: View) {
        when(view.id) {
            R.id.searchByCityButton -> {
                prepareSearchByCityName()
            }
            R.id.searchByLocationButton -> {
                prepareSearchByGeolocation()
            }
        }
    }

    /**
     * Uses a city name to generate the correct URL
     */
    private fun getUrlForCity(cityName: String): String {
        return "https://api.openweathermap.org/data/2.5/weather?q=$cityName&appid=$openWeatherMapAPIKey"
    }

    /**
     * Uses a latitude and longitude to generate the correct URL
     */
    private fun getUrlForGeolocation(latitude: Double, longitude: Double): String {
        return "https://api.openweathermap.org/data/2.5/weather?lat=$latitude&lon=$longitude&appid=$openWeatherMapAPIKey"
    }

    /**
     * Converts degrees from Kelvin to Celsius
     */
    private fun convertKelvinToCelsius(temp: Float): Float {
        return temp - 273.15f
    }

    /**
     * Converts degrees from Celsius to Kelvin
     */
    private fun convertKelvinToFahrenheit(temp: Float): Float {
        return (temp - 273.15f) * (9.0f/5.0f) + 32.0f
    }

    /**
     * Attempts to find the weather based on the city name the user entered. If the field is empty,
     * a warning message is displayed to the user.
     */
    private fun prepareSearchByCityName() {
        if (searchEditText.text.isEmpty()) {
            Toast.makeText(this, "Please enter a city to search for!", Toast.LENGTH_SHORT).show()
        } else {
            cityName = searchEditText.text.toString()
            searchEditText.text.clear()

            weatherDataLoadingStarted()
            GlobalScope.launch {
                getWeatherData(getUrlForCity("$cityName,US"))
            }
        }
    }

    /**
     * Attempts to retrieve the users latitude and longitude to find the weather based on their current location.
     * If the user has not granted location permission, an alert dialog prompts the user to allow location access.
     */
    private fun prepareSearchByGeolocation() {
        // Check if we have location access permissions, if we don't, prompt the user to enable location services
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            // Attempt to get the latitude/longitude and weather
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    latitude =  location.latitude
                    longitude = location.longitude

                    cityName = "Current Weather"
                    searchEditText.text.clear()

                    weatherDataLoadingStarted()
                    GlobalScope.launch {
                        getWeatherData(getUrlForGeolocation(latitude, longitude))
                    }
                }
            }
            // If an error occurs, inform the user.
            fusedLocationClient.lastLocation.addOnFailureListener { exception ->

                weatherDataLoadingComplete()
                Toast.makeText(this, "Error Occurred Getting Location.\nPlease Try Again.", Toast.LENGTH_SHORT).show()
            }
        } else {
            AlertDialog.Builder(this)
                .setTitle("Enable Location")
                .setMessage("Your Location Setting Is Set To 'Off'.\nPlease Enable Location Services For This App.")
                .setPositiveButton("Location Settings") { paramDialogInterface, paramInt ->
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_REQUEST_CODE)
                }
                .setNegativeButton("Dismiss") { _, which ->
                    Toast.makeText(this, "This App Will Not Work Without Location  Services!", Toast.LENGTH_LONG).show()
                }
                .show()
            weatherDataLoadingComplete()
        }
    }

    /**
     * Updates the UI to display the current temperature.
     */
    private fun updateUI() {
        val newLabel = when(isFahrenheit) {
            true -> "$cityName, ${temperatureInFahrenheit}°F"
            else -> "$cityName, ${temperatureInCelsius}°C"
        }

        cityTextView.text = newLabel
    }

    /**
     * Displays a spinning ProgressBar to indicate the request is being process.
     */
    private fun weatherDataLoadingStarted() {
        loadingConstraintView.visibility = View.VISIBLE
        degreeToggleButton.visibility = View.GONE
        searchByCityButton.visibility = View.GONE
        searchByLocationButton.visibility = View.GONE
        searchEditText.visibility = View.GONE
    }

    /**
     * Hides the spinning ProgressBar to indicated the request has complete.
     */
    private fun weatherDataLoadingComplete() {
        loadingConstraintView.visibility = View.GONE
        degreeToggleButton.visibility = View.VISIBLE
        searchByCityButton.visibility = View.VISIBLE
        searchByLocationButton.visibility = View.VISIBLE
        searchEditText.visibility = View.VISIBLE
    }

    /**
     * Hides the soft keyboard.
     */
    private fun hideKeyboard(view: View) {
        val inputMethodManager: InputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * Launches a new courotine to make an HTTP Request to the OpenWeatherMap server.
     * This function uses Google's Volley API to make the request and return an array of Strings
     * that will be converted to a JSON Object and parsed to obtain the data we need.
     */
    private suspend fun getWeatherData(url: String) = withContext(Dispatchers.IO) {
        Log.d("8bt", "Making HTTP request for $url")

        try {
            // Instantiate the Volley RequestQueue.
            val queue = Volley.newRequestQueue(this@MainActivity)

            // Request a string response from the provided URL.
            val stringRequest = StringRequest(Request.Method.GET, url,
                Response.Listener<String> {

                    // Convert string response to a JSON Object
                    val jsonObject = JSONObject(it)

                    // Get temperature in Kelvin
                    val temperatureInKelvin = jsonObject.getJSONObject("main").get("temp").toString().toFloat()

                    // Convert Kelvin to Celsius and Fahrenheit
                    temperatureInCelsius = round(convertKelvinToCelsius(temperatureInKelvin) * 100) / 100
                    temperatureInFahrenheit = round(convertKelvinToFahrenheit(temperatureInKelvin) * 100) / 100

                    weatherDataLoadingComplete()
                    updateUI()

                    Log.d("8bt", "JSON Object for $cityName: $jsonObject")
                    Log.d("8bt", "Temperature for $cityName $temperatureInKelvin k")
                },
                Response.ErrorListener {
                    weatherDataLoadingComplete()
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


