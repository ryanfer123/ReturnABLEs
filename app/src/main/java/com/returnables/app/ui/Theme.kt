package com.returnables.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LedgerLight = lightColorScheme(
    primary=Color(0xFF2447DA),onPrimary=Color.White,
    primaryContainer=Color(0xFFE4E9FF),onPrimaryContainer=Color(0xFF172034),
    secondary=Color(0xFF4D5670),secondaryContainer=Color(0xFFE8ECF5),
    tertiary=Color(0xFF3D5710),onTertiary=Color.White,
    tertiaryContainer=Color(0xFFD8FF70),onTertiaryContainer=Color(0xFF172034),
    background=Color(0xFFFAF8F0),onBackground=Color(0xFF172034),
    surface=Color.White,onSurface=Color(0xFF172034),
    surfaceVariant=Color(0xFFEFEEE6),onSurfaceVariant=Color(0xFF50596D),
    outline=Color(0xFF172034),outlineVariant=Color(0xFFCDD0D7),
    error=Color(0xFFA12536),errorContainer=Color(0xFFFFE8EB),onErrorContainer=Color(0xFF641426),
)
private val LedgerDark = darkColorScheme(
    primary=Color(0xFFB9C6FF),onPrimary=Color(0xFF112675),
    primaryContainer=Color(0xFF2945A3),onPrimaryContainer=Color(0xFFE5E9FF),
    secondary=Color(0xFFC5CEE5),secondaryContainer=Color(0xFF343E56),
    tertiary=Color(0xFFD8FF70),onTertiary=Color(0xFF223000),
    tertiaryContainer=Color(0xFFD8FF70),onTertiaryContainer=Color(0xFF172034),
    background=Color(0xFF131923),onBackground=Color(0xFFEBEEF7),
    surface=Color(0xFF202735),onSurface=Color(0xFFEBEEF7),
    surfaceVariant=Color(0xFF30394A),onSurfaceVariant=Color(0xFFC4CDDD),
    outline=Color(0xFFAEB9D0),outlineVariant=Color(0xFF465269),
    error=Color(0xFFFFB1BC),errorContainer=Color(0xFF641426),onErrorContainer=Color(0xFFFFDCE2),
)
private val LedgerType = Typography(
    headlineLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Black,fontSize=32.sp,lineHeight=37.sp),
    headlineMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=26.sp,lineHeight=32.sp),
    titleLarge=TextStyle(fontWeight=FontWeight.Bold,fontSize=21.sp,lineHeight=28.sp),
    titleMedium=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=17.sp,lineHeight=24.sp),
    bodyLarge=TextStyle(fontSize=16.sp,lineHeight=24.sp),
    bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp),
    labelLarge=TextStyle(fontWeight=FontWeight.Bold,fontSize=14.sp,lineHeight=20.sp),
)
@Composable
fun ReturnablesTheme(darkTheme:Boolean=isSystemInDarkTheme(),content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=if(darkTheme) LedgerDark else LedgerLight,typography=LedgerType,
        shapes=Shapes(extraSmall=RoundedCornerShape(4.dp),small=RoundedCornerShape(6.dp),medium=RoundedCornerShape(10.dp),large=RoundedCornerShape(16.dp),extraLarge=RoundedCornerShape(20.dp)),content=content)
}
