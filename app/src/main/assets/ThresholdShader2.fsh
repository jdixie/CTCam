#extension GL_OES_EGL_image_external : require
varying mediump vec2 textureCoordinate;
precision mediump float;


uniform samplerExternalOES videoFrame;
uniform int colorsTracked;
uniform float inputColor[32];
uniform float deltaEThreshold;
uniform float hueThreshold;

float abs(float a, float b)
{
	float val = a - b;

	if(val < 0.0)
		val = -1.0 * val;

	return val;
}

vec3 rgbToLab(vec3 inColor)
{
	float x;
	float y;
	float z;
	float l;
	float a;
	float b;

	//RGB to XYZ color
	if(inColor.r > 0.04045)
		x = pow(((inColor.r + 0.055) / 1.055), 2.4);
	else
		x = inColor.r / 12.92;

	if(inColor.g > 0.04045)
		y = pow(((inColor.g + 0.055) / 1.055), 2.4);
	else
		y = inColor.g / 12.92;

    if(inColor.b > 0.04045)
		z = pow(((inColor.b + 0.055) / 1.055), 2.4);
	else
		z = inColor.b / 12.92;

	//for final RGB to XYZ, multiply the previous x, y, and z by 100,
	//but since we will divide the final values by 95.047, 100, and
	//108.883, for optimizations I have combined that all below
	l = (x * 0.4124 + y * 0.3576 + z * 0.1805) / 0.95047;
	a = (x * 0.2126 + y * 0.7152 + z * 0.0722) / 1.00000;
	b = (x * 0.0193 + y * 0.1192 + z * 0.9505) / 1.08883;

	if(l > 0.008856)
		l = pow(l, (1.0 / 3.0));
	else
		l = (7.787 * l) + (16.0 / 116.0);

	if(a > 0.008856)
		a = pow(a, (1.0 / 3.0));
	else
		a = (7.787 * a) + (16.0 / 116.0);

    if(b > 0.008856)
		b = pow(b, (1.0 / 3.0));
	else
		b = (7.787 * b) + (16.0 / 116.0);

	//return L*, a*, b*
	return vec3((116.0 * a) - 16.0, 500.0 * (l - a), 200.0 * (a - b));
}

//CIE94
float deltaE(vec3 labA, vec3 labB)
{
	float deltaL = labA.x - labB.x;
	float deltaA = labA.y - labB.y;
	float deltaB = labA.z - labB.z;
	float c1 = sqrt(labA.y * labA.y + labA.z * labA.z);
	float c2 = sqrt(labB.y * labB.y + labB.z * labB.z);
	float deltaC = c1 - c2;
	float deltaH = deltaA * deltaA + deltaB * deltaB - deltaC * deltaC;
	if(deltaH < 0.0)
		deltaH = 0.0;
	else
		deltaH = sqrt(deltaH);
	float sc = 1.0 + 0.045 * c1;
	float sh = 1.0 + 0.015 * c1;
	float deltaLKlsl = deltaL / 1.0;
	float deltaCkcsc = deltaC / sc;
	float deltaHkhsh = deltaH / sh;
	float deltaE = deltaLKlsl * deltaLKlsl + deltaCkcsc * deltaCkcsc + deltaHkhsh * deltaHkhsh;
	if(deltaE < 0.0)
		deltaE = 0.0;
	else
		deltaE = sqrt(deltaE);
	return deltaE;
}

void main()
{
	float d;
	highp float result;
	vec4 pixelColor;
	vec4 ic;
	int tracked = 0;

	pixelColor = texture2D(videoFrame, textureCoordinate);
	vec3 g = vec3(0.2125, 0.7154, 0.0721);
	float gray = dot(pixelColor.rgb, g);
	//float gray = (pixelColor.r + pixelColor.g + pixelColor.b) / 3.0;


	for (int i = 0; i < 8; i++){
		if(i < colorsTracked){
			ic = vec4((inputColor[i*4]), (inputColor[(i*4)+1]), (inputColor[(i*4)+2]), (inputColor[(i*4)+3]));
			vec3 labA = rgbToLab(vec3(pixelColor.r, pixelColor.g, pixelColor.b));
			vec3 labB = rgbToLab(vec3(ic.r, ic.g, ic.b));
			float d = deltaE(labA, labB);

			//put in checks for zero values and figure out the 180 on the hue

			float hueA = atan(labA.z, labA.y);
			float hueB = atan(labB.z, labB.y);

			//if(d <= deltaEThreshold && abs(hueA, hueB) <= hueThreshold)
			//	tracked = 1;
			d = distance(vec3(gray, gray, gray), ic.rgb);
			//result = step(deltaEThreshold, gray);
			if(d < deltaEThreshold)
				tracked = 1;
		}
	}

	vec4 finalColor = vec4(pixelColor.r, pixelColor.g, pixelColor.b, 1.0);

	if(tracked == 0)
		finalColor = vec4(result, result, result, 0.0);
	else
		finalColor = vec4(1.0, 1.0, 1.0, 1.0);

	//gl_FragColor = (d > threshold) ? pixelColor : vec4(gray, gray, gray, pixelColor.a);
	gl_FragColor = finalColor;
}