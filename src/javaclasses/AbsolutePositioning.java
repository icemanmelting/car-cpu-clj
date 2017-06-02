package javaclasses;

import javafx.animation.Animation;


/**
 * Created by iceman on 18/07/2016.
 */
public class AbsolutePositioning {
    private int height;
    private int width;
    private int posX;
    private int posY;
    private int windowHeight;
    private int windowWidth;
    private Animation animation;

    private int order;

    public AbsolutePositioning()
    {
        windowHeight= Screen.WINDOW_HEIGHT;
        windowWidth = Screen.WINDOW_WIDTH;
    }

    public int getHeight()
    {
        return height;
    }

    public void setHeight(int height)
    {
        this.height = height;
    }

    public int getWidth()
    {
        return width;
    }

    public void setWidth(int width)
    {
        this.width = width;
    }

    public int getPosX()
    {
        return posX;
    }

    public void setPosX(int posX)
    {
        this.posX = posX;
    }

    public int getPosY()
    {
        return posY;
    }

    public void setPosY(int posY)
    {
        this.posY = posY;
    }

    public int getWindowHeight()
    {
        return windowHeight;
    }

    public void setWindowHeight(int windowHeight)
    {
        this.windowHeight = windowHeight;
    }

    public int getWindowWidth()
    {
        return windowWidth;
    }

    public void setWindowWidth(int windowWidth)
    {
        this.windowWidth = windowWidth;
    }

    public int getOrder()
    {
        return order;
    }

    public void setOrder(int order)
    {
        this.order = order;
    }

    public Animation getAnimation()
    {
        return animation;
    }

    public void setAnimation(Animation animation)
    {
        this.animation = animation;
    }

}
