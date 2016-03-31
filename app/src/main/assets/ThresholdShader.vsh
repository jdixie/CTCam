#extension GL_OES_EGL_image_external : require
//hsl is a cone color model: hue is degrees 0-360, saturation is from center of cone to the edge,
//lightness is the verticality with the bottom being black, top white.

varying mediump vec2 textureCoordinate;
precision mediump float;


uniform samplerExternalOES videoFrame;
uniform int colorsTracked;
uniform float inputColor[32];
uniform float threshold;

float abs(float a, float b)
{
	float val = a - b;

	if(val < 0.0)
		val = -1.0 * val;

	return val;
}

vec3 rgbToHsl(vec3 inColor)
{
	float r = inColor.x;
	float g = inColor.y;
	float b = inColor.z;
	float maxRGB;
	float minRGB;
	float chroma;
	float h;
	float s;
	float l;

	maxRGB = max(r, g);
	maxRGB = max(maxRGB, b);
	minRGB = min(r, g);
	minRGB = min(minRGB, b);
	chroma = maxRGB - minRGB;
	l = (maxRGB + minRGB) / 2.0;

	if(chroma == 0)
	{
		h = 0.0;
		s = 0.0;
	}
	else
	{
		s = chroma / (1.0 - abs((2.0 * l), 1.0));
		if(maxRGB == r)
			h = mod(((g - b) / chroma), 6.0) * 60;
		else if(maxRGB == g)
			h = ((b - r) / chroma + 2.0) * 60;
		else
			h = ((r - g) / chroma + 4.0) * 60;
	}

	return new vec3(h, s, l);
}

void main()
{
	float d;
	vec4 pixelColor;
	vec4 ic;
	int tracked = 0;

	pixelColor = texture2D(videoFrame, textureCoordinate);
	float gray = (pixelColor.r + pixelColor.g + pixelColor.b) / 3.0;

	for (int i = 0; i < 8; i++){
		if(i < colorsTracked){
			ic = vec4((inputColor[i*4]), (inputColor[(i*4)+1]), (inputColor[(i*4)+2]), (inputColor[(i*4)+3]));
			vec3 hslPixel = rgbToHsl(vec3(pixelColor.r, pixelColor.g, pixelColor.b));
			vec3 hslInput = rgbToHsl(vec3(ic.r, ic.g, ic.b));
			float d = deltaE(labA, labB);
			if(d < threshold)
				tracked = 1;
		}
	}

	vec4 finalColor = vec4(pixelColor.r, pixelColor.g, pixelColor.b, 1.0);

	if(tracked == 0)
		finalColor = vec4(gray, gray, gray, 0.0);
	else
		finalColor = vec4(1.0, 1.0, 1.0, 1.0);

	//gl_FragColor = (d > threshold) ? pixelColor : vec4(gray, gray, gray, pixelColor.a);
	gl_FragColor = finalColor;
}