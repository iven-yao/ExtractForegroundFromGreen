
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import javax.swing.*;


public class ImageDisplay {


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

	class YUV{
		double y,u,v;
		public YUV(double y, double u, double v) {
			this.y = y;
			this.u = u;
			this.v = v;
		}

		public String toString(){
			return "("+y+", "+u+" ,"+ v +")";
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

		public boolean equals(Object o) {
			if(o == this) return true;
			if(!(o instanceof RGB)) return false;
			
			RGB c = (RGB) o;
			return (this.r==c.r && this.g == c.g && this.b == c.b);
		}
	}


	JFrame frame, frame_output;
	JLabel lbIm1, lbIm2;
	BufferedImage outputVideo[];
	int width = 640; // default image width and height
	int height = 480;
	int frames = 480; 
	int kernel = 3;// 3 or 5
	int h_threshold = 50; // h threshold, out of h_greencenter +- h_threshold should be extracted as foreground
	int h_greencenter = 120; 
	double s_threshold = 0.25; // s less than s_threshold should be extracted as foreground
	double v_threshold = 0.3; // v less than v_threshold should be extracted as foreground 
	long fps = 1000/24; // default
	YUV[][] formerYUV;
	int[][] foregroundMap;

	private HSV RGBtoHSV(RGB rgb) {
		double h,s,v, cmax, cmin, diff, r, g, b;

		r = (double)rgb.r / 255;
		g = (double)rgb.g / 255;
		b = (double)rgb.b / 255;

		cmax = Math.max(r, Math.max(g, b));
		cmin = Math.min(r, Math.min(g, b));
		diff = cmax - cmin;

		h = diff == 0? 0: cmax == r? 60*((g-b)/diff)%6: cmax == g? 60*(((b-r)/diff) +2): 60*(((r-g)/diff)+4);
		s = cmax == 0? 0: diff / cmax;
		v = cmax;

		

		return new HSV(h, s, v);
	}

	private YUV RGBtoYUV(RGB rgb){
		double y = 0.299*rgb.r + 0.587*rgb.g + 0.114*rgb.b;
		double u = 0.596*rgb.r - 0.274*rgb.g - 0.322*rgb.b;
		double v = 0.211*rgb.r - 0.523*rgb.g + 0.312*rgb.b;

		return new YUV(y,u,v);
	}

	/** Mode 0
	 *  comparing to former frame, extract the pixels that changed
	 *  merge with background video
	 */
	private void backgroundSubstraction(String imgPath, String backgroundPath, BufferedImage img, int i)
	{
		try
		{
			boolean first = false;
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

			if(formerYUV == null) {
				formerYUV = new YUV[height][width];
				first = true;
			}

			RGB[][] outputRGB = new RGB[height][width];
			int ind = 0;
			
			for(int y = 0; y < height; y++)
			{
				for(int x = 0; x < width; x++)
				{
					

					int r = Byte.toUnsignedInt(bytes[ind]);
					int g = Byte.toUnsignedInt(bytes[ind+height*width]);
					int b = Byte.toUnsignedInt(bytes[ind+height*width*2]); 

					RGB rgb = new RGB(r,g,b);
					//convert to yuv space and compare to former frames' average yuv
					YUV yuv = RGBtoYUV(rgb);
					YUV fyuv = formerYUV[y][x];

					if(first) {
						
						r = Byte.toUnsignedInt(backgroundBytes[ind]);
						g = Byte.toUnsignedInt(backgroundBytes[ind+height*width]);
						b = Byte.toUnsignedInt(backgroundBytes[ind+height*width*2]); 
					
						formerYUV[y][x] = yuv;
					} else {

						// calculate yuv diff by three channels' average in 0~1 scale
						double diffYUV = (Math.abs(yuv.y - fyuv.y)/255
									+ Math.abs(yuv.u - fyuv.u)/255
									+ Math.abs(yuv.v - fyuv.v)/255)/3;

						boolean shouldBePreserved = (diffYUV > 0.00); 

						if(!shouldBePreserved){
							
							r = Byte.toUnsignedInt(backgroundBytes[ind]);
							g = Byte.toUnsignedInt(backgroundBytes[ind+height*width]);
							b = Byte.toUnsignedInt(backgroundBytes[ind+height*width*2]); 
							
						} 
						// formerYUV[y][x] = new YUV((yuv.y+fyuv.y*(i-1))/i,(yuv.u+fyuv.u*(i-1))/i,(yuv.v+fyuv.v*(i-1))/i);
						formerYUV[y][x] = yuv;
					}

					
					outputRGB[y][x] = new RGB(r,g,b);
					ind++;
				}
			}

			// set img
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

	/** Mode 1
	 *  detect green screen and crop the foreground out of it and merge with background
	 *  convert RGB into HSV color space
	 *  specify green part with H,S,V thresholds
	 *  substitute those pixels with background pixels
	 *  perform gaussian blur to blend foreground and background
	 *  output to bufferedImage img
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

			// int ind = 0;

			RGB[][] outputRGB = new RGB[height][width];
			foregroundMap = new int[height][width];

			for(int y = 0; y < height; y++)
			{
				for(int x = 0; x < width; x++)
				{
					int r = Byte.toUnsignedInt(bytes[y*width+x]);
					int g = Byte.toUnsignedInt(bytes[y*width+x+height*width]);
					int b = Byte.toUnsignedInt(bytes[y*width+x+height*width*2]); 

					// if(x==1 && y==1) {System.out.println("["+r+","+g+","+b+"]");}

					RGB rgb = new RGB(r,g,b);
					HSV hsv = RGBtoHSV(rgb);

					if(Math.abs(hsv.h-h_greencenter) <= h_threshold && hsv.s >= s_threshold && hsv.v >= v_threshold)  {
						r = Byte.toUnsignedInt(backgroundBytes[y*width+x]);
						g = Byte.toUnsignedInt(backgroundBytes[y*width+x+height*width]);
						b = Byte.toUnsignedInt(backgroundBytes[y*width+x+height*width*2]); 

						foregroundMap[y][x] = 0;

					} else {
						r = (rgb.r);
						g = (rgb.g);
						b = (rgb.b);

						foregroundMap[y][x] = 1;
					}

					outputRGB[y][x] = new RGB(r,g,b);
				}
			}

			// boundary blending
			// perform gaussian blur on the boundary pixels
			for(int y = 3; y < height -3; y++)
			{
				for(int x = 3; x < width -3; x++)
				{
					if(checkBoundary(foregroundMap, y, x, foregroundMap[y][x])) {
						outputRGB[y][x] = gaussianBlur3x3(outputRGB, y, x);
					}
					
				}
			}

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

	private boolean checkBoundary(int[][] map, int y, int x, int val){
		for(int i = -3; i <= 3; i++) {
			for(int j = -3; j <= 3; j++ ){
				if(map[y+i][x+j] != val) return true;
			}
		}

		return false;
	}

	private RGB gaussianBlur3x3(RGB[][] outputRGB, int y, int x) {
		int[][] coefficient = {{1,2,1},{2,4,2},{1,2,1}};
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

		return new RGB(r, g, b);
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
				backgroundSubstraction(foregroundRGB, backgroundRGB, outputVideo[i], i);
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
		ImageDisplay ren = new ImageDisplay();
		try {
			ren.showIms(args);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
