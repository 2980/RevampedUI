/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ichabod;

import java.awt.Color;

/**
 *
 * @author unouser
 */
class ColorIntPair implements Comparable<ColorIntPair> {
    
    public Color color;
    
    public int count;

    @Override
    public int compareTo(ColorIntPair o) {
        return this.count - o.count;
    }
    
}
