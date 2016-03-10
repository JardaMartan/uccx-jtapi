package com.cisco.jmartan.image.hzs;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class ImageTest {

	public static void main(String[] args) {
		BufferedImage img = new BufferedImage(414, 70,
				BufferedImage.TYPE_INT_ARGB);
		Graphics g = img.createGraphics();
		g.setColor(Color.BLACK);
		g.setFont(new Font("SansSerif", Font.PLAIN, 10));
		g.drawString("Hello", 15, 15);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			ImageIO.write(img, "jpg", baos);

			byte[] bytes = baos.toByteArray();
			ImageIO.write(img, "png", new File("testimage.png"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
