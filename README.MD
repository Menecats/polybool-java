# polybool-java

Java port of [https://github.com/velipso/polybooljs](https://github.com/velipso/polybooljs).

Boolean operations on polygons (union, intersection, difference, xor).

# Features

1. Clips polygons for all boolean operations
2. Removes unnecessary vertices
3. Handles segments that are coincident (overlap perfectly, share vertices, one inside the other,  
   etc)
4. Uses formulas that take floating point irregularities into account (via configurable epsilon)
5. Provides an API for constructing efficient sequences of operations
6. Support for GeoJSON `"Polygon"` and `"MultiPolygon"` types (experimental)

# Installing

To use polybool-java, you need to use the following Maven dependency:

```xml
<!-- Maven -->
<dependency>
    <groupId>com.menecats</groupId>
    <artifactId>polybool-java</artifactId>
    <version>1.0.1</version>
</dependency>
```

```groovy
// Gradle (Groovy)
implementation 'com.menecats:polybool-java:1.0.1'
```

or download jars from Maven repository (or via quick links on
the [Release page](https://github.com/Menecats/polybool-java/releases))

# Example

```java 
import com.menecats.polybool.Epsilon;
import com.menecats.polybool.PolyBool;
import com.menecats.polybool.models.Polygon;

import static com.menecats.polybool.helpers.PolyBoolHelper.*;

public class PolyBoolExample {
    public static void main(String[] args) {
        Epsilon eps = epsilon();
  
        Polygon intersection = PolyBool.intersect(  
                eps,  
                polygon(  
                        region(  
                                point(50, 50),  
                                point(150, 150),  
                                point(190, 50)  
                        ),  
                        region(  
                                point(130, 50),  
                                point(290, 150),  
                                point(290, 50)  
                        )  
                ),  
                polygon(  
                        region(  
                                point(110, 20),
                                point(110, 110),
                                point(20, 20)
                        ),
                        region(
                                point(130, 170),
                                point(130, 20),
                                point(260, 20),
                                point(260, 170)
                        )
                )
        );

        System.out.println(intersection);
        // Polygon { inverted: false, regions: [
        //     [[50.0, 50.0], [110.0, 50.0], [110.0, 110.0]],
        //     [[178.0, 80.0], [130.0, 50.0], [130.0, 130.0], [150.0, 150.0]],
        //     [[178.0, 80.0], [190.0, 50.0], [260.0, 50.0], [260.0, 131.25]]
        // ]}
    }
}
```

![PolyBool Example](https://github.com/voidqk/polybooljs/raw/master/example.png)

## Basic Usage

```java
Epsilon eps=new Epsilon();

        Polygon poly=PolyBool.union(eps,poly1,poly2);
        Polygon poly=PolyBool.intersect(eps,poly1,poly2);
        Polygon poly=PolyBool.difference(eps,poly1,poly2); // poly1 - poly2
        Polygon poly=PolyBool.differenceRev(eps,poly1,poly2); // poly2 - poly1
        Polygon poly=PolyBool.xor(eps,poly1,poly2);
```  

Where `poly1`, `poly2`, and the return value are `Polygon` objects.

# GeoJSON (experimental)

There are also functions for converting between the native polygon format and  
[GeoJSON](https://tools.ietf.org/html/rfc7946).

Note: These functions are currently **experimental**, and I'm hoping users can provide feedback.  
Please comment in [this issue on GitHub](https://github.com/voidqk/polybooljs/issues/7) -- including  
letting me know if it's working as expected. I don't use GeoJSON, but I thought I would take a  
crack at conversion functions.

Use the following functions:

```java  
Geometry<?> geojson = PolyBool.polygonToGeoJSON(poly);  
Polygon poly = PolyBool.polygonFromGeoJSON(geojson);  
```  

Only `"Polygon"` and `"MultiPolygon"` types are supported.

# Core API

```java
Epsilon eps=new Epsilon();

        Segments segments=PolyBool.segments(eps,polygon);
        Combined combined=PolyBool.combine(eps,segments1,segments2);

        Segments segments=PolyBool.selectUnion(combined);
        Segments segments=PolyBool.selectIntersect(combined);
        Segments segments=PolyBool.selectDifference(combined);
        Segments segments=PolyBool.selectDifferenceRev(combined);
        Segments segments=PolyBool.selectXor(combined);

        Polygon polygon=PolyBool.polygon(eps,segments);  
```    

Depending on your needs, it might be more efficient to construct your own sequence of operations  
using the lower-level API. Note that `PolyBool.union`, `PolyBool.intersect`, etc, are just thin  
wrappers for convenience.

There are three types of objects you will encounter in the core API:

1. Polygons (discussed above, this is a list of regions and an `inverted` flag)
2. Segments
3. Combined Segments

The basic flow chart of the API is:

![PolyBool API Flow Chart](https://github.com/voidqk/polybooljs/raw/master/flowchart.png)

You start by converting Polygons to Segments using `PolyBool.segments(eps, poly)`.

You convert Segments to Combined Segments using `PolyBool.combine(eps, seg1, seg2)`.

You select the resulting Segments from the Combined Segments using one of the selection operators  
`PolyBool.selectUnion(combined)`, `PolyBool.selectIntersect(combined)`, etc. These selection  
functions return Segments.

Once you're done, you convert the Segments back to Polygons using `PolyBool.polygon(eps, segments)`.

Each transition is costly, so you want to navigate wisely. The selection transition is the least  
costly.

## Advanced Example 1

Suppose you wanted to union a list of polygons together. The naive way to do it would be:

```java  
// works but not efficient

Polygon result = polygons[0];
for (int i = 1; i < polygons.length; i++)  
    result = PolyBool.union(eps, result, polygons[i]);  
  
return result;  
```  

Instead, it's more efficient to use the core API directly, like this:

```java
// works AND efficient  
Segments segments=PolyBool.segments(eps,polygons[0]);
        for(int i=1;i<polygons.length;i++){
        Segments seg2=PolyBool.segments(eps,polygons[i]);
        Combined comb=PolyBool.combine(eps,segments,seg2);
        segments=PolyBool.selectUnion(comb);
        }
        return PolyBool.polygon(eps,segments);  
```  

## Advanced Example 2

Suppose you want to calculate all operations on two polygons. The naive way to do it would be:

```java  
// works but not efficient
Map<String, Polygon> ops = new HashMap<>();

ops.put("union",         PolyBool.union        (eps, poly1, poly2));
ops.put("intersect",     PolyBool.intersect    (eps, poly1, poly2));
ops.put("difference",    PolyBool.difference   (eps, poly1, poly2));
ops.put("differenceRev", PolyBool.differenceRev(eps, poly1, poly2));
ops.put("xor",           PolyBool.xor          (eps, poly1, poly2));

return operations;
```  

Instead, it's more efficient to use the core API directly, like this:

```java  
// works AND efficient  
Segments seg1 = PolyBool.segments(eps, poly1);
Segments seg2 = PolyBool.segments(eps, poly2);
Combined comb = PolyBool.combine(eps, seg1, seg2);  

Map<String, Polygon> ops= new HashMap<>();

ops.put("union",         PolyBool.polygon(eps, PolyBool.selectUnion        (eps, poly1, poly2)));
ops.put("intersect",     PolyBool.polygon(eps, PolyBool.selectIntersect    (eps, poly1, poly2)));
ops.put("difference",    PolyBool.polygon(eps, PolyBool.selectDifference   (eps, poly1, poly2)));
ops.put("differenceRev", PolyBool.polygon(eps, PolyBool.selectDifferenceRev(eps, poly1, poly2)));
ops.put("xor",           PolyBool.polygon(eps, PolyBool.selectXor          (eps, poly1, poly2)));

return ops;
```  

## Advanced Example 3

As an added bonus, just going from Polygon to Segments and back performs simplification on the  
polygon.

Suppose you have garbage polygon data and just want to clean it up. The naive way to do it would  
be:

```java
// union the polygon with nothing in order to clean up the data  
// works but not efficient  
Polygon cleaned=PolyBool.union(eps,polygon,new Polygon());  
```  

Instead, skip the combination and selection phase:

```java
// works AND efficient  
Polygon cleaned=PolyBool.polygon(eps,PolyBool.segments(eps,polygon));
```  

# Epsilon

Due to the beauty of floating point reality, floating point calculations are not exactly perfect.  
This is a problem when trying to detect whether lines are on top of each other, or if vertices are  
exactly the same.

Normally you would expect this to work:

```java
if(A==B){
        /* A and B are equal */;
        }else{
        /* A and B are not equal */;
        }
```  

But for inexact floating point math, instead we use:

```java
if(Math.abs(A-B)<epsilon){
        /* A and B are equal */;
        }else{
        /* A and B are not equal */;
        }
```  

You can set the epsilon while you invoke polybool functions by creating an `Epsilon` instance

```java
Epsilon eps=new Epsilon();

        PolyBool.segments(eps,poly);
```

You can specify a custom epsilon value while you instantiate an `Epsilon` object or you can change it on an existing one

```java
Epsilon eps=new Epsilon(myCustomEpsilonValue);

        eps.epsilon(anotherCustomEpsilonValue);

        PolyBool.segments(eps,poly);
```

The default epsilon value is `0.0000000001 (1e-10)`.

If your polygons are really really large or really really tiny, then you will probably have to come  
up with your own epsilon value -- otherwise, the default should be fine.

If `PolyBool` detects that your epsilon is too small or too large, it will throw an error:

```  
PolyBool: Zero-length segment detected; your epsilon is probably too small or too large  
```

## Experimental Epsilon changes

There is an `ExperimentalEpsilon` class that implements some experimantal changes from the
PR [#8](https://github.com/velipso/polybooljs/pull/8) that aims to fix some bugs, but is not fully tested.