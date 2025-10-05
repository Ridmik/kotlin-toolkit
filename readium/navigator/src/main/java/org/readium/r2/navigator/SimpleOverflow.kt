/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.navigator

import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.InternalReadiumApi

@InternalReadiumApi
@OptIn(ExperimentalReadiumApi::class)
public data class SimpleOverflow(
    override val readingProgression: ReadingProgression,
    override val scroll: Boolean?,
    override val axis: Axis,
) : OverflowableNavigator.Overflow {

    // need secondary constructor for backward compatibility!
    // @Deprecated("Keeping it for backward compatibility. Consider changing the scroll to nullable boolean in extended classes / usages.")
    // adding deprecation crashes due to strict mode policy in readium
    public constructor(
        readingProgression: ReadingProgression,
        scroll: Boolean,
        axis: Axis
    ) : this(
        readingProgression = readingProgression,
        scroll = scroll as Boolean?, // type cast is a must to disambiguate the secondary constructor from the primary constructor.
        axis = axis
    )
}
