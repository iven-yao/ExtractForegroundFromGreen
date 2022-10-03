
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import javax.swing.*;


public class VideoDisplay {

	JFrame frame, frame_output;
	JLabel lbIm1, lbIm2;
	BufferedImage outputVideo[];
	int width = 640; // default image width and height
	int height = 480;
	int frames = 480;
	int h_threshold = 50;
	int h_greencenter = 120;
	double s_threshold = 0.22;
	double v_threshold = 0.28;
	long fps = 1000/24;
	boolean useColorBackground = false;

	class HSV{
		double h,s,v;
		public HSV(double h, double s, double v) {
			this.h = h;
			this.s = s;
			this.v = v;
		}

		public String toString(){
			return "("+h+", "+s+" ,"+ v +")";
		}
	}

	class RGB{
		int r,g,b;
		public RGB(int r, int g, int b) {
			this.r = r;
			this.g = g;
			this.b = b;
		}

		public String toString(){
			return "("+r+", "+g+" ,"+ b +")";
		}
	}

	private RGB HSVtoRGB(HSV hsv) {
		int r, g, b;
		double c, x, m, r_1, g_1, b_1;

		c = hsv.s * hsv.v;
		x = c * (1 - Math.abs((hsv.h / 60) % 2 -1));
		m = hsv.v - c;

		if(hsv.h >= 300) {
			r_1 = c;
			g_1 = 0;
			b_1 = x;
		} else if(hsv.h >= 240) {
			r_1 = x;
			g_1 = 0;
			b_1 = c;
		} else if(hsv.h >= 180) {
			r_1 = 0;
			g_1 = x;
			b_1 = c;
		} else if(hsv.h >= 120) {
			r_1 = 0;
			g_1 = c;
			b_1 = x;
		} else if(hsv.h >= 60) {
			r_1 = x;
			g_1 = c;
			b_1 = 0;
		} else {
			r_1 = c;
			g_1 = x;
			b_1 = 0;
		}

		r = (int) Math.round((r_1 + m) * 255);
		g = (int) Math.round((g_1 + m) * 255);
		b = (int) Math.round((b_1 + m) * 255);

		r = r<0?0:r>255?255:r;
		g = g<0?0:g>255?255:g;
		b = b<0?0:b>255?255:b;

		return new RGB(r, g, b);
	}

	private HSV RGBtoHSV(RGB rgb) {
		double h,s,v, cmax, cmin, diff, r, g, b;

		r = (double)rgb.r / 255;
		g = (double)rgb.g / 255;
		b = (double)rgb.b / 255;

		cmax = Math.max(r, Math.max(g, b));
		cmin = Math.min(r, Math.min(g, b));
		diff = cmax - cmin;

		h = cmax == 0? 0: cmax == r? 60*((g-b)/diff)%6: cmax == g? 60*(((b-r)/diff) +2): 60*(((r-g)/diff)+4);
		s = cmax == 0? 0: diff / cmax;
		v = cmax;

		return new HSV(h, s, v);
	}

	/** Read Image RGB
	 *  Reads the image of given width and height at the given imgPath into the provided BufferedImage.
	 */
	private void greenScreenDetect(String imgPath, String backgroundPath, BufferedImage img)
	{
		try
		{
			int frameLength = width*height*3;

			File file = new File(imgPath);
			RandomAccessFile raf = new RandomAccessFile(file, "r");
			raf.seek(0);

			byte[] bytes = new byte[frameLength];

			raf.read(bytes);

			File backgroundFile = new File(backgroundPath);
			RandomAccessFile backgroundRaf = new RandomAccessFile(backgroundFile, "r");
			backgroundRaf.seek(0);
			byte[] backgroundBytes = new byte[frameLength];

			backgroundRaf.read(backgroundBytes);

			int ind = 0;

			RGB[][] outputRGB = new RGB[height][width];

			for(int y = 0; y < height; y++)
			{
				for(int x = 0; x < width; x++)
				{
					int r = Byte.toUnsignedInt(bytes[ind]);
					int g = Byte.toUnsignedInt(bytes[ind+height*width]);
					int b = Byte.toUnsignedInt(bytes[ind+height*width*2]); 

					RGB rgb = new RGB(r,g,b);
					HSV hsv = RGBtoHSV(rgb);
					if(Math.abs(hsv.h - h_greencenter) > h_threshold || hsv.s < s_threshold || hsv.v < v_threshold){
						rgb = HSVtoRGB(hsv);

						r = (rgb.r);
						g = (rgb.g);
						b = (rgb.b);
					} else {
						if(useColorBackground){
							r = 255;
							g = 0;
							b = 0;
						} else {
							r = Byte.toUnsignedInt(backgroundBytes[ind]);
							g = Byte.toUnsignedInt(backgroundBytes[ind+height*width]);
							b = Byte.toUnsignedInt(backgroundBytes[ind+height*width*2]); 
						}
					}

					// if(Math.abs(hsv.h - h_greencenter) < h_threshold && hsv.s > s_threshold && hsv.v > v_threshold){
					// 	if(useColorBackground){
					// 		r = 255;
					// 		g = 0;
					// 		b = 0;
					// 	} else {
					// 		r = Byte.toUnsignedInt(backgroundBytes[ind]);
					// 		g = Byte.toUnsignedInt(backgroundBytes[ind+height*width]);
					// 		b = Byte.toUnsignedInt(backgroundBytes[ind+height*width*2]); 
					// 	}
					// } else {

					// 	rgb = HSVtoRGB(hsv);

					// 	r = (rgb.r);
					// 	g = (rgb.g);
					// 	b = (rgb.b);
					// }

					outputRGB[y][x] = new RGB(r,g,b);
					ind++;
				}
			}

			// perform Gaussian blur
			outputRGB = gaussianBlur5x5Row(outputRGB);
			// outputRGB = gaussianBlur5x5Col(outputRGB);

			// set img rgb
			setImgRGB(outputRGB, img);

			raf.close();
			backgroundRaf.close();
		}
		catch (FileNotFoundException e) 
		{
			e.printStackTrace();
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		} finally {
			
		}
	}

	private RGB[][] gaussianBlur3x3Row(RGB[][] outputRGB) {
		int[][] coefficient = {{1,2,1},{2,4,2},{1,2,1}};
		for(int y = 0; y < height; y++) 
		{
			for(int x = 0; x < width; x++)
			{
				int r = 0, g = 0, b = 0;
				for(int i = -1; i <=1; i++){
					for(int j = -1; j <=1; j++){
						r += outputRGB[y+i][x+j].r * coefficient[i+1][j+1];
						g += outputRGB[y+i][x+j].g * coefficient[i+1][j+1];
						b += outputRGB[y+i][x+j].b * coefficient[i+1][j+1];
					}
				}
				r /= 16;
				g /= 16;
				b /= 16;

				outputRGB[y][x] = new RGB(r, g, b);
			}
		}

		return outputRGB;
	}

	private RGB[][] gaussianBlur3x3Col(RGB[][] outputRGB) {
		int[][] coefficient = {{1,2,1},{2,4,2},{1,2,1}};
		for(int x = 0; x < width; x++) 
		{
			for(int y = 0; y < height; y++)
			{
				int r = 0, g = 0, b = 0;
				for(int i = -1; i <=1; i++){
					for(int j = -1; j <=1; j++){
						r += outputRGB[y+i][x+j].r * coefficient[i+1][j+1];
						g += outputRGB[y+i][x+j].g * coefficient[i+1][j+1];
						b += outputRGB[y+i][x+j].b * coefficient[i+1][j+1];
					}
				}
				r /= 16;
				g /= 16;
				b /= 16;

				outputRGB[y][x] = new RGB(r, g, b);
			}
		}
		return outputRGB;
	}

	private RGB[][] gaussianBlur5x5Row(RGB[][] outputRGB) {
		int[][] coefficient = {{1,4,7,4,1},{4,16,26,16,4},{7,26,41,26,7},{4,16,26,16,4},{1,4,7,4,1}};

		for(int y = 2; y < height-2; y++){
			for(int x = 2; x < width-2; x++) {
				int r = 0, g = 0, b = 0;
				for(int i = -2; i <=2; i++){
					for(int j = -2; j <=2; j++){
						r += outputRGB[y+i][x+j].r * coefficient[i+2][j+2];
						g += outputRGB[y+i][x+j].g * coefficient[i+2][j+2];
						b += outputRGB[y+i][x+j].b * coefficient[i+2][j+2];
					}
				}
				r /= 273;
				g /= 273;
				b /= 273;

				outputRGB[y][x] = new RGB(r, g, b);
			}
		}

		return outputRGB;
	}

	private RGB[][] gaussianBlur5x5Col(RGB[][] outputRGB) {
		int[][] coefficient = {{1,4,7,4,1},{4,16,26,16,4},{7,26,41,26,7},{4,16,26,16,4},{1,4,7,4,1}};

		for(int x = 2; x < width-2; x++){
			for(int y = 2; y < height-2; y++) {
				int r = 0, g = 0, b = 0;
				for(int i = -2; i <=2; i++){
					for(int j = -2; j <=2; j++){
						r += outputRGB[y+i][x+j].r * coefficient[i+2][j+2];
						g += outputRGB[y+i][x+j].g * coefficient[i+2][j+2];
						b += outputRGB[y+i][x+j].b * coefficient[i+2][j+2];
					}
				}
				r /= 273;
				g /= 273;
				b /= 273;

				outputRGB[y][x] = new RGB(r, g, b);
			}
		}

		return outputRGB;
	}

	private void setImgRGB(RGB[][] outputRGB, BufferedImage img) {
		for(int y = 0; y < height; y++)
		{
			for(int x = 0; x < width; x++)
			{

				int r = outputRGB[y][x].r;
				int g = outputRGB[y][x].g;
				int b = outputRGB[y][x].b;
				int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);		
				img.setRGB(x,y,pix);
			}
		}
	}

	private void substractMoving(String imgPath, String backgroundPath, BufferedImage img)
	{
		try
		{
			int frameLength = width*height*3;

			File file = new File(imgPath);
			RandomAccessFile raf = new RandomAccessFile(file, "r");
			raf.seek(0);

			byte[] bytes = new byte[frameLength];

			raf.read(bytes);

			File backgroundFile = new File(backgroundPath);
			RandomAccessFile backgroundRaf = new RandomAccessFile(backgroundFile, "r");
			backgroundRaf.seek(0);
			byte[] backgroundBytes = new byte[frameLength];

			backgroundRaf.read(backgroundBytes);

			int ind = 0;
			int former_r = 0, former_g = 0, former_b = 0;
			// read input, covert to yuv space
			for(int y = 0; y < height; y++)
			{
				for(int x = 0; x < width; x++)
				{
					int r = Byte.toUnsignedInt(bytes[ind]);
					int g = Byte.toUnsignedInt(bytes[ind+height*width]);
					int b = Byte.toUnsignedInt(bytes[ind+height*width*2]); 

					RGB rgb = new RGB(r,g,b);
					HSV hsv = RGBtoHSV(rgb);
					if(Math.abs(hsv.h - h_greencenter) < h_threshold && hsv.s > s_threshold && hsv.v > v_threshold){
						// hsv.h = 0;
						// hsv.s = 1;
						// hsv.v = 1;
						r = Byte.toUnsignedInt(backgroundBytes[ind]);
						g = Byte.toUnsignedInt(backgroundBytes[ind+height*width]);
						b = Byte.toUnsignedInt(backgroundBytes[ind+height*width*2]); 
					} else {

						rgb = HSVtoRGB(hsv);

						r = rgb.r;
						g = rgb.g;
						b = rgb.b;
					}

					int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
					
					img.setRGB(x,y,pix);
					ind++;
				}
			}

			raf.close();
			backgroundRaf.close();
		}
		catch (FileNotFoundException e) 
		{
			e.printStackTrace();
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		} finally {
			
		}
	}

	public void showIms(String[] args) throws InterruptedException{

		// Read a parameter from command line
		// String param1 = args[1];
		// System.out.println("The second parameter was: " + param1);

		// Read in the specified image
		outputVideo = new BufferedImage[480];

		char ch = args[0].charAt(args[0].length()-1);
		if( ch == '\\' || ch == '/') {
			args[0] = args[0].substring(0, args[0].length()-1);
		}
		
		ch = args[1].charAt(args[1].length()-1);
		if( ch == '\\' || ch == '/') {
			args[1] = args[1].substring(0, args[1].length()-1);
		}

		String fPath = args[0].replace("\\","/");
		String bPath = args[1].replace("\\","/");

		String filename = fPath.substring(fPath.lastIndexOf('/')+1);
		String path = fPath.concat("/").concat(filename).concat(".");
		String background = bPath.substring(bPath.lastIndexOf('/')+1);
		String backgroundPath = bPath.concat("/").concat(background).concat(".");
		int mode = Integer.parseInt(args[2]);

		if(mode == 1) {
			for(int i = 0; i < frames; i++) {
				outputVideo[i] = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
				String foregroundRGB = path.concat(String.format("%04d", i)).concat(".rgb");
				String backgroundRGB = backgroundPath.concat(String.format("%04d", i)).concat(".rgb");
				greenScreenDetect(foregroundRGB, backgroundRGB, outputVideo[i]);
			}
		}

		if(mode == 0) {
			for(int i = 0; i < frames; i++) {
				outputVideo[i] = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
				String foregroundRGB = path.concat(String.format("%04d", i)).concat(".rgb");
				String backgroundRGB = backgroundPath.concat(String.format("%04d", i)).concat(".rgb");
				substractMoving(foregroundRGB, backgroundRGB, outputVideo[i]);
			}
		}

		// Use label to display the image
		frame = new JFrame();
		GridBagLayout gLayout = new GridBagLayout();
		frame.getContentPane().setLayout(gLayout);

		lbIm1 = new JLabel(new ImageIcon(outputVideo[0]));
		// lbIm2 = new JLabel(new ImageIcon(scaled));

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.CENTER;
		c.weightx = 0.5;
		c.gridx = 0;
		c.gridy = 0;

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 1;
		frame.getContentPane().add(lbIm1, c);

		frame.pack();
		frame.setVisible(true);

		for(int i = 0; i < frames; i++) {
			Thread.sleep(fps);
			lbIm1.setIcon(new ImageIcon(outputVideo[i]));
		}
	}

	public static void main(String[] args) {
		VideoDisplay ren = new VideoDisplay();
		try {
			ren.showIms(args);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
