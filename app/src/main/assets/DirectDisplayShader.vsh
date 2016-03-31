attribute vec4 position;
attribute vec4 inputTextureCoordinate;
uniform mat4 textureMatrix;
uniform mat4 MVPMatrix;

varying vec2 textureCoordinate;

void main()
{
    gl_Position = position; //MVPMatrix * position;
    textureCoordinate = (textureMatrix * inputTextureCoordinate).xy;
}