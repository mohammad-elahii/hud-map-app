package com.example.hudmapapp.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.hudmapapp.R
import androidx.compose.material3.Typography


val Typography: Typography
    get() = Typography(
        displayLarge = TextStyle(
            fontFamily = nunitoFamily,
            fontWeight = FontWeight.W900,
            fontSize = 40.sp,
        ),
        displayMedium = TextStyle(
            fontFamily = nunitoFamily,
            fontWeight = FontWeight.W800,
            fontSize = 32.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = nunitoFamily,
            fontWeight = FontWeight.W800,
            fontSize = 28.sp,
        ),


        headlineMedium = TextStyle(
            fontFamily = nunitoFamily,
            fontWeight = FontWeight.W800,
            fontSize = 24.sp,
        ),

        titleLarge = TextStyle(
            fontFamily = nunitoFamily,
            fontWeight = FontWeight.W700,
            fontSize = 20.sp,
        ),

        titleMedium = TextStyle(
            fontFamily = poppinsFamily,
            fontWeight = FontWeight.W600,
            fontSize = 16.sp,
        ),

        bodyLarge = TextStyle(
            fontFamily = poppinsFamily,
            fontWeight = FontWeight.W400,
            fontSize = 16.sp,
        ),

        bodyMedium = TextStyle(
            fontFamily = poppinsFamily,
            fontWeight = FontWeight.W400,
            fontSize = 14.sp,
        ),

        bodySmall = TextStyle(
            fontFamily = poppinsFamily,
            fontWeight = FontWeight.W400,
            fontSize = 12.sp,
        ),

        labelLarge = TextStyle(
            fontFamily = poppinsFamily,
            fontWeight = FontWeight.W600,
            fontSize = 14.sp,
        ),

        labelMedium = TextStyle(
            fontFamily = poppinsFamily,
            fontWeight = FontWeight.W600,
            fontSize = 12.sp,
        ),

    )

val nunitoFamily = FontFamily(
    Font(R.font.nunitoregular, FontWeight.W400),
    Font(R.font.nunitobold, FontWeight.W700),
    Font(R.font.nunitoextrabold, FontWeight.W800),
    Font(R.font.nunitoblack, FontWeight.W900),
)

val poppinsFamily = FontFamily(
    fonts = listOf(
        Font(R.font.poppinsblack, FontWeight.W600),
        Font(R.font.poppinsmedium, FontWeight.W400),
        )
)