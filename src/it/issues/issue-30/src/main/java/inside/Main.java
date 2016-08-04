// @formatter:off
/**
 * Copyright (C) 2016 Matthieu Brouillard [http://oss.brouillard.fr/jgitver-maven-plugin] (matthieu@brouillard.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// @formatter:on

package inside;

import outside.IntSetBuilder;

public class Main
{
    public static double codePointsOverLength( String value )
    {
        IntSetBuilder isb = new IntSetBuilder();
        value.codePoints().forEach( isb );
        return isb.size() / (double) isb.accepted;
    }

    public static void main( String[] args )
    {
        String value = args.length > 0
            ? String.join( " ", java.util.Arrays.asList(args) )
            : "The quick brown fox jumps over the lazy dog.";
        System.out.println( codePointsOverLength(value) + "  " + value );
    }
}
