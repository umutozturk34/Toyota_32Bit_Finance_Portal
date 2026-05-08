package com.finance.news.model;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.event.*;
import com.finance.common.repository.*;

public enum NewsCategory {
    CRYPTO,
    BORSA_ISTANBUL,
    BORSA_SIRKETLERI,
    TAHVIL_BONO,
    PARITE,
    GENEL_FINANS,
    EMTIA
}
